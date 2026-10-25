package com.iotiq.partnererp.modules.products.service;

import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.products.dto.ProductImportResult;
import com.iotiq.partnererp.modules.products.dto.ProductImportResult.Outcome;
import com.iotiq.partnererp.modules.products.entity.Product;
import com.iotiq.partnererp.modules.products.entity.ProductCategory;
import com.iotiq.partnererp.modules.products.mapper.ProductMapper;
import com.iotiq.partnererp.modules.products.repository.ProductRepository;
import com.iotiq.partnererp.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Price sheet CSV import, export and template (BUSINESS_RULES §5.1 with the D-18 fixes, P7-06):
 * <ul>
 *   <li>headers match by lower-case prefix, as in the source; {@code sku}, {@code b2c_name} and {@code cp_name}
 *       columns are required;</li>
 *   <li>rows are upserted by SKU, ignoring case (an existing SKU keeps its stored spelling); a missing column or a blank cell keeps the stored value (new
 *       products get the defaults: unit {@code nos}, GST from the company settings);</li>
 *   <li>a new product needs a category, B2C name, partner name and partner price; unknown categories are rejected,
 *       never defaulted;</li>
 *   <li>every row is validated like the product form; rows with errors are reported and skipped;</li>
 *   <li>{@code dryRun} previews without saving; a commit saves all valid rows in one transaction.</li>
 * </ul>
 * Importing needs {@code PRODUCTS:VIEW_INTERNAL_B2C} on top of Write, because the file carries B2C fields.
 */
@Service
public class ProductImportService {

    public static final int MAX_ROWS = 5000;
    public static final int MAX_BYTES = 1024 * 1024;

    /** Export and template columns, in source order (§5.1). */
    public static final List<String> INTERNAL_HEADER = List.of("sku", "category", "b2c_name", "cp_name",
            "cp_alternatives (| separated)", "hsn", "gst_percent", "unit", "b2c_price", "cp_price", "cost", "active");
    /** Export columns for callers without the internal action: no B2C name, alternatives, B2C price or cost. */
    public static final List<String> PARTNER_HEADER = List.of("sku", "category", "cp_name", "hsn", "gst_percent",
            "unit", "cp_price", "active");

    /** Import columns: header prefix, product field (for messages), display name. */
    enum Column {
        SKU("sku", "sku", "sku"),
        CATEGORY("category", "category", "category"),
        B2C_NAME("b2c_name", "b2cName", "b2c_name"),
        CP_NAME("cp_name", "partnerName", "cp_name"),
        ALIASES("cp_alt", "aliases", "cp_alternatives"),
        HSN("hsn", "hsn", "hsn"),
        GST("gst", "gstPercent", "gst_percent"),
        UNIT("unit", "unit", "unit"),
        B2C_PRICE("b2c_price", "b2cPrice", "b2c_price"),
        CP_PRICE("cp_price", "partnerPrice", "cp_price"),
        COST("cost", "costPrice", "cost");

        final String prefix;
        final String field;
        /** Template header name, used in messages and the recognised-column list. */
        final String name;

        Column(String prefix, String field, String name) {
            this.prefix = prefix;
            this.field = field;
            this.name = name;
        }

        static String header(String field) {
            return Arrays.stream(values()).filter(c -> c.field.equals(field)).findFirst().map(c -> c.name)
                    .orElse(field);
        }
    }

    private final ProductRepository repository;
    private final ProductService productService;
    private final AuditService auditService;

    public ProductImportService(ProductRepository repository, ProductService productService, AuditService auditService) {
        this.repository = repository;
        this.productService = productService;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------------------------------- export

    /** The filtered sheet as CSV; B2C name, alternatives, B2C price and cost only with the internal action. */
    @Transactional(readOnly = true)
    public String export(ProductService.Filters filters, AuthenticatedUser user) {
        boolean internal = ProductService.canViewInternal(user);
        List<List<String>> rows = productService.searchAll(filters, user).stream()
                .map(p -> internal ? internalRow(p) : partnerRow(p)).toList();
        return ProductCsv.write(internal ? INTERNAL_HEADER : PARTNER_HEADER, rows);
    }

    /** Header plus one example row in a valid category (D-18 fixes the source's "Switching"). */
    public static String template() {
        return ProductCsv.write(INTERNAL_HEADER.subList(0, INTERNAL_HEADER.size() - 1), List.of(List.of(
                "SW-4M", ProductCategory.SMART_TOUCH_INTERFACES.getLabel(), "4-module smart touch panel",
                "4-gang capacitive control module",
                "Quad-channel touch controller|4-gang capacitive control module|Four-circuit tactile switching unit",
                "8536", "18", "nos", "6500", "5200", "")));
    }

    private static List<String> internalRow(Product p) {
        return List.of(p.getSku(), p.getCategory().getLabel(), p.getB2cName(), p.getPartnerName(),
                String.join("|", p.getAliases()), nz(p.getHsn()), plain(p.getGstPercent()), p.getUnit(),
                plain(p.getB2cPrice()), plain(p.getPartnerPrice()), plain(p.getCostPrice()), yesNo(p.isActive()));
    }

    private static List<String> partnerRow(Product p) {
        return List.of(p.getSku(), p.getCategory().getLabel(), p.getPartnerName(), nz(p.getHsn()),
                plain(p.getGstPercent()), p.getUnit(), plain(p.getPartnerPrice()), yesNo(p.isActive()));
    }

    // ------------------------------------------------------------------------------------------- import

    @Transactional
    public ProductImportResult importCsv(byte[] content, boolean dryRun, AuthenticatedUser user) {
        if (!ProductService.canViewInternal(user)) {
            throw ProductService.internalRequired("Importing products needs the View internal B2C prices "
                    + "permission, because the file carries B2C names and prices.");
        }
        if (content == null || content.length == 0) {
            throw ValidationException.of("file", "Choose a CSV file to import.");
        }
        if (content.length > MAX_BYTES) {
            throw ValidationException.of("file", "The file is larger than 1 MB.");
        }
        List<List<String>> rows = ProductCsv.parse(decode(content));
        if (rows.size() < 2) {
            throw ValidationException.of("file", "The file has no data rows.");
        }
        if (rows.size() - 1 > MAX_ROWS) {
            throw ValidationException.of("file", "Import at most " + MAX_ROWS + " rows at a time.");
        }
        Map<Column, Integer> columns = columns(rows.get(0));
        if (!columns.containsKey(Column.SKU) || !columns.containsKey(Column.B2C_NAME) || !columns.containsKey(Column.CP_NAME)) {
            throw ValidationException.of("file", "Columns sku, b2c_name and cp_name are required. Download the "
                    + "template to see the layout.");
        }

        List<List<String>> data = rows.subList(1, rows.size());
        Set<String> skus = data.stream().map(r -> cell(r, columns.get(Column.SKU)))
                .filter(s -> s != null && !s.isEmpty()).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Map<String, Product> existing = new HashMap<>();
        if (!skus.isEmpty()) {
            existing.putAll(repository.findBySkuLowerIn(skus).stream()
                    .collect(Collectors.toMap(p -> p.getSku().toLowerCase(Locale.ROOT), Function.identity())));
        }
        BigDecimal defaultGst = productService.defaultGst();

        List<ProductImportResult.Row> results = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int failed = 0;
        for (int i = 0; i < data.size(); i++) {
            Planned plan = plan(i + 1, data.get(i), columns, existing, seen, defaultGst);
            results.add(plan.result());
            switch (plan.result().outcome()) {
                case CREATE -> created++;
                case UPDATE -> updated++;
                case UNCHANGED -> unchanged++;
                case ERROR -> failed++;
            }
            if (!dryRun && plan.values() != null && plan.result().outcome() != Outcome.UNCHANGED) {
                save(plan, existing);
            }
        }
        if (!dryRun && created + updated > 0) {
            repository.flush();
            auditService.record(AuditActions.PRODUCT_IMPORT_COMPLETED, AuditActions.ENTITY_PRODUCT, null, null,
                    AuditService.values("rows", data.size(), "created", created, "updated", updated,
                            "unchanged", unchanged, "failed", failed));
        }
        List<String> recognised = Arrays.stream(Column.values()).filter(columns::containsKey).map(c -> c.name).toList();
        return new ProductImportResult(dryRun, data.size(), created, updated, unchanged, failed, recognised, results);
    }

    private void save(Planned plan, Map<String, Product> existing) {
        Values v = plan.values();
        Product product = plan.existing();
        Map<String, Object> before = product == null ? null : ProductMapper.auditSnapshot(product);
        if (product == null) {
            product = new Product();
            product.setActive(true);
        }
        product.setSku(v.sku());
        product.setCategory(v.category());
        product.setUnit(v.unit());
        product.setHsn(v.hsn());
        product.setB2cName(v.b2cName());
        product.setPartnerName(v.partnerName());
        product.setAliases(new ArrayList<>(v.aliases()));
        product.setB2cPrice(v.b2cPrice());
        product.setPartnerPrice(v.partnerPrice());
        product.setGstPercent(v.gstPercent());
        product.setCostPrice(v.costPrice());
        repository.save(product);
        existing.put(v.sku().toLowerCase(Locale.ROOT), product);
        Map<String, Object> after = ProductMapper.auditSnapshot(product);
        if (before == null) {
            auditService.record(AuditActions.PRODUCT_IMPORTED, AuditActions.ENTITY_PRODUCT, product.getId(), null, after);
        } else {
            Set<String> changed = ProductService.changedKeys(before, after);
            auditService.record(AuditActions.PRODUCT_IMPORTED, AuditActions.ENTITY_PRODUCT, product.getId(),
                    ProductService.only(before, changed), ProductService.only(after, changed));
        }
    }

    /** The product values a row asks for, or the errors that stop it. */
    record Values(String sku, ProductCategory category, String unit, String hsn, String b2cName, String partnerName,
                  List<String> aliases, BigDecimal b2cPrice, BigDecimal partnerPrice, BigDecimal gstPercent,
                  BigDecimal costPrice) {

        Map<String, Object> snapshot() {
            return AuditService.values("sku", sku, "category", category, "unit", unit, "hsn", hsn,
                    "partnerName", partnerName, "partnerPrice", partnerPrice, "gstPercent", gstPercent,
                    "b2cName", b2cName, "aliases", aliases, "b2cPrice", b2cPrice, "costPrice", costPrice);
        }
    }

    record Planned(ProductImportResult.Row result, Values values, Product existing) {
    }

    Planned plan(int rowNumber, List<String> row, Map<Column, Integer> columns, Map<String, Product> existing,
                 Map<String, Integer> seen, BigDecimal defaultGst) {
        List<FieldError> errors = new ArrayList<>();
        String rawSku = cell(row, columns.get(Column.SKU));
        if (rawSku == null || rawSku.isEmpty()) {
            return error(rowNumber, null, List.of("sku: SKU is missing."));
        }
        String sku = ProductRules.sku(rawSku, errors);
        String key = sku.toLowerCase(Locale.ROOT);
        Integer firstRow = seen.putIfAbsent(key, rowNumber);
        if (firstRow != null) {
            return error(rowNumber, sku, List.of("sku: This SKU is already on row " + firstRow + " of the file."));
        }
        Product current = existing.get(key);
        boolean creating = current == null;
        if (!creating) {
            // Matching ignores case; the stored spelling of an existing SKU is kept.
            sku = current.getSku();
        }

        ProductCategory category = creating ? null : current.getCategory();
        String categoryCell = cell(row, columns.get(Column.CATEGORY));
        if (categoryCell != null && !categoryCell.isEmpty()) {
            category = ProductRules.category(categoryCell).orElse(null);
            if (category == null) {
                errors.add(new FieldError("category", "Unknown category \"" + categoryCell + "\". Use one of: "
                        + Arrays.stream(ProductCategory.values()).map(ProductCategory::getLabel)
                        .collect(Collectors.joining(", ")) + "."));
            }
        } else if (creating) {
            errors.add(new FieldError("category", "Category is required for a new product."));
        }

        String b2cName = text(row, columns, Column.B2C_NAME, creating ? null : current.getB2cName());
        b2cName = b2cName == null ? required(errors, "b2cName", "B2C name") : ProductRules.text(b2cName, "b2cName", ProductRules.MAX_NAME, errors);
        String partnerName = text(row, columns, Column.CP_NAME, creating ? null : current.getPartnerName());
        partnerName = partnerName == null ? required(errors, "partnerName", "Partner-facing name")
                : ProductRules.text(partnerName, "partnerName", ProductRules.MAX_NAME, errors);

        String aliasCell = cell(row, columns.get(Column.ALIASES));
        List<String> aliases = aliasCell == null || aliasCell.isEmpty()
                ? (creating ? List.of() : List.copyOf(current.getAliases()))
                : ProductRules.aliases(Arrays.asList(aliasCell.split("\\|")), errors);

        String hsnCell = cell(row, columns.get(Column.HSN));
        String hsn = hsnCell == null || hsnCell.isEmpty() ? (creating ? null : current.getHsn())
                : ProductRules.hsn(hsnCell, errors);
        String unitCell = cell(row, columns.get(Column.UNIT));
        String unit = unitCell == null || unitCell.isEmpty() ? (creating ? ProductRules.DEFAULT_UNIT : current.getUnit())
                : ProductRules.unit(unitCell, errors);

        BigDecimal gst = number(row, columns, Column.GST, creating ? defaultGst : current.getGstPercent(), errors);
        if (gst != null) {
            gst = ProductRules.gst(gst, errors);
        }
        BigDecimal partnerPrice = amount(row, columns, Column.CP_PRICE, creating ? null : current.getPartnerPrice(), errors);
        if (partnerPrice == null && errors.stream().noneMatch(e -> e.field().equals("partnerPrice"))) {
            errors.add(new FieldError("partnerPrice", "Partner price is required for a new product."));
        }
        BigDecimal b2cPrice = amount(row, columns, Column.B2C_PRICE, creating ? null : current.getB2cPrice(), errors);
        BigDecimal costPrice = amount(row, columns, Column.COST, creating ? null : current.getCostPrice(), errors);

        if (!errors.isEmpty()) {
            return error(rowNumber, sku, errors.stream().map(e -> Column.header(e.field()) + ": " + e.message()).toList());
        }
        Values values = new Values(sku, category, unit, hsn, b2cName, partnerName, aliases, b2cPrice, partnerPrice,
                gst, costPrice);
        List<String> warnings = ProductRules.warnings(partnerName, b2cName, partnerPrice, b2cPrice, costPrice);
        if (creating) {
            return new Planned(new ProductImportResult.Row(rowNumber, sku, Outcome.CREATE, List.of(), List.of(), warnings),
                    values, null);
        }
        Map<String, Object> before = ProductMapper.auditSnapshot(current);
        before.remove("active");
        Map<String, Object> after = values.snapshot();
        Set<String> changed = ProductService.changedKeys(before, after);
        List<String> changes = changed.stream().map(k -> describe(k, before.get(k), after.get(k))).toList();
        return new Planned(new ProductImportResult.Row(rowNumber, sku, changed.isEmpty() ? Outcome.UNCHANGED : Outcome.UPDATE,
                changes, List.of(), warnings), values, current);
    }

    private static Planned error(int rowNumber, String sku, List<String> errors) {
        return new Planned(new ProductImportResult.Row(rowNumber, sku, Outcome.ERROR, List.of(), errors, List.of()),
                null, null);
    }

    private static String required(List<FieldError> errors, String field, String label) {
        errors.add(new FieldError(field, label + " is required for a new product."));
        return null;
    }

    /** "cp_price: 5749.00 → 5999.00"; alternative names are summarised by count. */
    private static String describe(String field, Object from, Object to) {
        String column = Column.header(field);
        if (from instanceof List<?> a && to instanceof List<?> b) {
            return column + ": " + a.size() + " → " + b.size() + " names";
        }
        return column + ": " + show(from) + " → " + show(to);
    }

    private static String show(Object value) {
        if (value == null) {
            return "(blank)";
        }
        if (value instanceof BigDecimal d) {
            return d.stripTrailingZeros().toPlainString();
        }
        if (value instanceof ProductCategory c) {
            return c.getLabel();
        }
        return "\"" + value + "\"";
    }

    // ------------------------------------------------------------------------------------------- cells

    static Map<Column, Integer> columns(List<String> header) {
        Map<Column, Integer> columns = new EnumMap<>(Column.class);
        List<String> names = header.stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        for (Column column : Column.values()) {
            for (int i = 0; i < names.size(); i++) {
                if (names.get(i).startsWith(column.prefix)) {
                    columns.put(column, i);
                    break;
                }
            }
        }
        return columns;
    }

    /** Trimmed cell; null when the column is absent, "" when the cell is blank or missing from a short row. */
    static String cell(List<String> row, Integer index) {
        if (index == null) {
            return null;
        }
        return index < row.size() ? ProductCsv.unguard(row.get(index).trim()) : "";
    }

    private static String text(List<String> row, Map<Column, Integer> columns, Column column, String current) {
        String value = cell(row, columns.get(column));
        return value == null || value.isEmpty() ? current : value;
    }

    private static BigDecimal number(List<String> row, Map<Column, Integer> columns, Column column, BigDecimal current,
                                     List<FieldError> errors) {
        String value = cell(row, columns.get(column));
        if (value == null || value.isEmpty()) {
            return current;
        }
        String cleaned = value.replace("₹", "").replace(",", "").replace("%", "").replace(" ", "");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            errors.add(new FieldError(column.field, "Enter a number (found \"" + value + "\")."));
            return null;
        }
    }

    private static BigDecimal amount(List<String> row, Map<Column, Integer> columns, Column column, BigDecimal current,
                                     List<FieldError> errors) {
        BigDecimal value = number(row, columns, column, current, errors);
        return value == current ? current : ProductRules.amount(value, column.field, errors);
    }

    /** UTF-8 (with or without BOM); files saved by spreadsheet programs as Windows-1252 are accepted too. */
    static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            return new String(content, Charset.forName("windows-1252"));
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
