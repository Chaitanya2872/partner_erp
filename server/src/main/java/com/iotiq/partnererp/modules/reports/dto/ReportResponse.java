package com.iotiq.partnererp.modules.reports.dto;

import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.PeriodView;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Report payloads (Phase 12). Columns are only those the caller may see; rows hold one value per column key plus
 * {@code id} and {@code link} (detail page path, or null). Totals cover every row matching the filters, not only the
 * page.
 */
public final class ReportResponse {

    private ReportResponse() {
    }

    public record ColumnView(String key, String label, String type, boolean sortable, boolean total) {
    }

    public record OptionView(String value, String label) {
    }

    /** A report as listed for the caller: whether it can be run and exported, and why not. */
    public record ReportInfo(String code, String title, String description, String module, boolean available,
                             String reason, boolean canExport, String dateBasis, List<ColumnView> columns,
                             String statusColumn, List<OptionView> statuses, boolean partnerFilter,
                             List<String> searchColumns, String defaultSort, boolean defaultDescending) {
    }

    public record Result(String code, String title, PeriodView period, List<ColumnView> columns,
                         List<Map<String, Object>> rows, Map<String, BigDecimal> totals, int page, int size,
                         long totalElements, int totalPages, String sort) {
    }

    public record PartnerOption(UUID id, String name, String code) {
    }
}
