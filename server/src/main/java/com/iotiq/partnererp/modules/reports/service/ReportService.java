package com.iotiq.partnererp.modules.reports.service;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.ResourceNotFoundException;
import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.PeriodView;
import com.iotiq.partnererp.modules.dashboard.repository.DashboardQueries;
import com.iotiq.partnererp.modules.partners.mapper.PartnerMapper;
import com.iotiq.partnererp.modules.payouts.service.PayoutRules;
import com.iotiq.partnererp.modules.products.service.ProductCsv;
import com.iotiq.partnererp.modules.reports.dto.ReportRequest;
import com.iotiq.partnererp.modules.reports.dto.ReportResponse.ColumnView;
import com.iotiq.partnererp.modules.reports.dto.ReportResponse.OptionView;
import com.iotiq.partnererp.modules.reports.dto.ReportResponse.PartnerOption;
import com.iotiq.partnererp.modules.reports.dto.ReportResponse.ReportInfo;
import com.iotiq.partnererp.modules.reports.dto.ReportResponse.Result;
import com.iotiq.partnererp.modules.reports.service.ReportDefinition.Column;
import com.iotiq.partnererp.modules.reports.service.ReportDefinition.Type;
import com.iotiq.partnererp.security.AuthenticatedUser;
import com.iotiq.partnererp.security.EffectivePermissions;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Runs the {@link ReportCatalog} reports (FR-10). Access is checked before any query: the report's module must be
 * readable with the ALL scope ({@link AnalyticsAccess}), columns from other modules are dropped for users who can't
 * read those, and an export also needs {@code REPORTS:EXPORT} (checked on the endpoint) and the module's own EXPORT
 * permission, so a report never exports more than the module export would. Filters, search and sorting only ever
 * reach SQL as bound parameters or whitelisted column keys.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    /** Most rows a CSV export may hold; a larger result must be narrowed with filters. */
    public static final int EXPORT_LIMIT = 10_000;
    static final int MAX_SEARCH = 100;
    private static final int PARTNER_OPTIONS = 20;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public ReportService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Every report with whether the caller can run and export it. */
    public List<ReportInfo> catalog(AuthenticatedUser user) {
        AnalyticsAccess access = AnalyticsAccess.of(user);
        return ReportCatalog.ALL.stream().map(def -> {
            boolean available = access.sees(def.module());
            List<Column> columns = available ? visibleColumns(def, access) : List.of();
            return new ReportInfo(def.code(), def.title(), def.description(), def.module().name(), available,
                    available ? null : access.restricted().get(def.module()),
                    available && canExport(user, def), def.dateBasis(), columns.stream().map(ReportService::view).toList(),
                    def.statusColumn(), def.statuses().stream().map(o -> new OptionView(o.value(), o.label())).toList(),
                    def.partnerFilter(), def.searchColumns().stream()
                    .filter(k -> columns.stream().anyMatch(c -> c.key().equals(k))).toList(),
                    def.defaultSort(), def.defaultDescending());
        }).toList();
    }

    /** One page of a report with totals over all matching rows. */
    public Result run(String code, ReportRequest request, AuthenticatedUser user) {
        ReportDefinition def = definition(code);
        AnalyticsAccess access = require(def, user);
        Query query = query(def, request, access);
        if (request.page() < 0) {
            throw ValidationException.of("page", "Page must be zero or greater.");
        }
        if (request.size() < 1 || request.size() > AppConstants.MAX_PAGE_SIZE) {
            throw ValidationException.of("size", "Size must be between 1 and " + AppConstants.MAX_PAGE_SIZE + ".");
        }
        Map<String, Object> aggregate = aggregate(query);
        long total = ((Number) aggregate.get("n")).longValue();
        MapSqlParameterSource params = new MapSqlParameterSource(query.params().getValues())
                .addValue("limit", request.size()).addValue("offset", (long) request.page() * request.size());
        List<Map<String, Object>> rows = jdbc.queryForList(query.select() + " LIMIT :limit OFFSET :offset", params)
                .stream().map(r -> row(r, query.columns())).toList();
        return new Result(def.code(), def.title(), query.period(), query.columns().stream().map(ReportService::view).toList(),
                rows, totals(aggregate, query.columns()), request.page(), request.size(), total,
                (int) Math.ceil(total / (double) request.size()), query.sort());
    }

    /** All matching rows as CSV (UTF-8 text; the controller adds the byte-order mark). */
    public String export(String code, ReportRequest request, AuthenticatedUser user) {
        ReportDefinition def = definition(code);
        AnalyticsAccess access = require(def, user);
        if (!canExport(user, def)) {
            throw new AccessDeniedException("Exporting " + def.code() + " needs " + def.module() + " export");
        }
        Query query = query(def, request, access);
        long total = ((Number) aggregate(query).get("n")).longValue();
        if (total > EXPORT_LIMIT) {
            throw new BusinessException("REPORT_TOO_LARGE", "This report has " + total + " rows; an export holds at most "
                    + EXPORT_LIMIT + ". Narrow the period or filters and try again.", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> raw : jdbc.queryForList(query.select(), query.params())) {
            Map<String, Object> row = row(raw, query.columns());
            rows.add(query.columns().stream().map(c -> csvValue(def, c, row.get(c.key()))).toList());
        }
        return ProductCsv.write(query.columns().stream().map(Column::label).toList(), rows);
    }

    /** Partners for the report partner filter; needs partners read with the ALL scope. */
    public List<PartnerOption> partnerOptions(String q, AuthenticatedUser user) {
        if (!AnalyticsAccess.of(user).sees(ModuleCode.PARTNERS)) {
            throw new AccessDeniedException("Partner options need partners read with the ALL scope");
        }
        MapSqlParameterSource params = new MapSqlParameterSource("limit", PARTNER_OPTIONS);
        String where = "";
        String text = PayoutRules.clean(q);
        if (text != null) {
            where = " WHERE lower(name) LIKE :like ESCAPE '\\' OR lower(partner_code) LIKE :like ESCAPE '\\'";
            params.addValue("like", PayoutRules.like(truncate(text)));
        }
        return jdbc.query("SELECT id, name, partner_code FROM partners" + where + " ORDER BY lower(name) LIMIT :limit",
                params, (rs, i) -> new PartnerOption(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("partner_code")));
    }

    public String filename(String code, ReportRequest request) {
        ReportPeriod period = request.resolve(LocalDate.now(clock));
        return "iotiq-report-" + definition(code).code() + "-" + period.from() + "-to-" + period.to() + ".csv";
    }

    // ------------------------------------------------------------------------------------------------ query building

    private record Query(String select, String aggregate, MapSqlParameterSource params, List<Column> columns,
                         PeriodView period, String sort) {
    }

    private Query query(ReportDefinition def, ReportRequest request, AnalyticsAccess access) {
        LocalDate today = LocalDate.now(clock);
        ReportPeriod period = request.resolve(today);
        MapSqlParameterSource params = DashboardQueries.params(period, today);
        List<Column> columns = visibleColumns(def, access);
        List<String> where = new ArrayList<>();

        String status = PayoutRules.clean(request.status());
        if (status != null) {
            if (def.statusColumn() == null || def.statuses().stream().noneMatch(o -> o.value().equals(status))) {
                throw ValidationException.of("status", "Choose one of: " + def.statuses().stream()
                        .map(ReportDefinition.Option::value).collect(Collectors.joining(", ")) + ".");
            }
            where.add("r." + def.statusColumn() + " = :status");
            params.addValue("status", status);
        }
        if (request.partnerId() != null) {
            if (!def.partnerFilter()) {
                throw ValidationException.of("partnerId", "This report can't be filtered by partner.");
            }
            where.add("r.partner_id = :partnerId");
            params.addValue("partnerId", request.partnerId());
        }
        String q = PayoutRules.clean(request.q());
        if (q != null) {
            List<String> searchable = def.searchColumns().stream()
                    .filter(k -> columns.stream().anyMatch(c -> c.key().equals(k))).toList();
            if (!searchable.isEmpty()) {
                where.add("(" + searchable.stream().map(k -> "lower(r." + k + "::text) LIKE :q ESCAPE '\\'")
                        .collect(Collectors.joining(" OR ")) + ")");
                params.addValue("q", PayoutRules.like(truncate(q)));
            }
        }
        String filtered = "FROM (" + def.sql() + ") r" + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where));

        String sortKey = def.defaultSort();
        boolean descending = def.defaultDescending();
        String sort = PayoutRules.clean(request.sort());
        if (sort != null) {
            String[] parts = sort.split(",");
            String key = parts[0].trim();
            Column column = columns.stream().filter(c -> c.key().equals(key) && c.sortable()).findFirst()
                    .orElseThrow(() -> ValidationException.of("sort", "Sort by one of: " + columns.stream()
                            .filter(Column::sortable).map(Column::key).collect(Collectors.joining(", ")) + "."));
            sortKey = column.key();
            descending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
        }
        String order = " ORDER BY r." + sortKey + (descending ? " DESC NULLS LAST" : " ASC NULLS LAST") + ", r.id";

        String sums = columns.stream().filter(Column::total)
                .map(c -> ", SUM(r." + c.key() + ") AS " + c.key()).collect(Collectors.joining());
        return new Query("SELECT r.* " + filtered + order, "SELECT COUNT(*) AS n" + sums + " " + filtered, params,
                columns, PeriodView.of(period, today, AppConstants.BUSINESS_ZONE.getId()),
                sortKey + "," + (descending ? "desc" : "asc"));
    }

    private Map<String, Object> aggregate(Query query) {
        return jdbc.queryForMap(query.aggregate(), query.params());
    }

    private static List<Column> visibleColumns(ReportDefinition def, AnalyticsAccess access) {
        return def.columns().stream().filter(c -> c.requires() == null || access.sees(c.requires())).toList();
    }

    private static ReportDefinition definition(String code) {
        return ReportCatalog.find(code).orElseThrow(() -> new ResourceNotFoundException("Report", code));
    }

    private static AnalyticsAccess require(ReportDefinition def, AuthenticatedUser user) {
        AnalyticsAccess access = AnalyticsAccess.of(user);
        if (!access.sees(def.module())) {
            throw new AccessDeniedException("Report " + def.code() + " needs " + def.module() + " read with the ALL scope");
        }
        return access;
    }

    private static boolean canExport(AuthenticatedUser user, ReportDefinition def) {
        EffectivePermissions p = user.permissions();
        return p.canPerform(ModuleCode.REPORTS.name(), EffectivePermissions.EXPORT)
                && p.canPerform(def.module().name(), EffectivePermissions.EXPORT);
    }

    // ------------------------------------------------------------------------------------------------ values

    /** Only visible columns, id and link leave the server; PAN is masked and timestamps become dates. */
    private static Map<String, Object> row(Map<String, Object> raw, List<Column> columns) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", Objects.toString(raw.get("id"), null));
        row.put("link", raw.get("link"));
        for (Column c : columns) {
            row.put(c.key(), value(c, raw.get(c.key())));
        }
        return row;
    }

    private static Object value(Column column, Object value) {
        if (value == null) {
            return null;
        }
        return switch (column.type()) {
            case MASKED -> PartnerMapper.mask(value.toString());
            case DATE -> value instanceof java.sql.Date d ? d.toLocalDate()
                    : value instanceof Timestamp t ? t.toLocalDateTime().toLocalDate() : value;
            case MONEY -> value instanceof BigDecimal b ? b.setScale(2, RoundingMode.HALF_UP) : value;
            case NUMBER, PERCENT -> value instanceof BigDecimal b ? plain(b) : value;
            case TEXT, CODE -> value.toString();
            case BOOLEAN -> value;
        };
    }

    private static BigDecimal plain(BigDecimal b) {
        BigDecimal stripped = b.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static Map<String, BigDecimal> totals(Map<String, Object> aggregate, List<Column> columns) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (Column c : columns) {
            if (c.total()) {
                Object v = aggregate.get(c.key());
                BigDecimal sum = v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
                totals.put(c.key(), c.type() == Type.MONEY ? sum.setScale(2, RoundingMode.HALF_UP) : plain(sum));
            }
        }
        return totals;
    }

    /** Plain values for spreadsheets: status codes as their labels, booleans as yes/no, amounts unformatted. */
    private static String csvValue(ReportDefinition def, Column column, Object value) {
        if (value == null) {
            return "";
        }
        if (column.key().equals(def.statusColumn())) {
            String code = value.toString();
            return def.statuses().stream().filter(o -> o.value().equals(code)).map(ReportDefinition.Option::label)
                    .findFirst().orElse(humanize(code));
        }
        return switch (column.type()) {
            case BOOLEAN -> Boolean.TRUE.equals(value) ? "yes" : "no";
            case MONEY, NUMBER, PERCENT -> value instanceof BigDecimal b ? b.toPlainString() : value.toString();
            case CODE -> humanize(value.toString());
            default -> value.toString();
        };
    }

    private static String humanize(String code) {
        String words = code.toLowerCase(Locale.ROOT).replace('_', ' ');
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String truncate(String text) {
        return text.length() <= MAX_SEARCH ? text : text.substring(0, MAX_SEARCH);
    }

    private static ColumnView view(Column c) {
        return new ColumnView(c.key(), c.label(), c.type().name(), c.sortable(), c.total());
    }
}
