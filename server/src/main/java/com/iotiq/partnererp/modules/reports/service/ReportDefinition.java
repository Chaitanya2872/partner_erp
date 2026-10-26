package com.iotiq.partnererp.modules.reports.service;

import com.iotiq.partnererp.modules.auth.entity.ModuleCode;

import java.util.List;
import java.util.Optional;

/**
 * One tabular report: a base SQL query producing one row per record, and how it may be filtered, searched, sorted and
 * totalled. The base query reads the period parameters ({@code :from}, {@code :to}, {@code :tsFrom}, {@code :tsTo},
 * {@code :today}, {@code :zone}) and must select {@code id} (stable tie-break), {@code partner_id} (null when the
 * report has no partner filter) and {@code link} (detail page, or null), plus every column by key. Column keys are
 * constants, so sorting and searching never put user input into SQL.
 *
 * @param module       module whose read permission (ALL scope) and export permission the report needs
 * @param dateBasis    what the period filters, as shown to users
 * @param statusColumn column the status filter applies to; null when there is none
 */
public record ReportDefinition(String code, String title, String description, ModuleCode module, String dateBasis,
                               List<Column> columns, String statusColumn, List<Option> statuses,
                               boolean partnerFilter, List<String> searchColumns, String defaultSort,
                               boolean defaultDescending, String sql) {

    public enum Type {
        TEXT, CODE, DATE, MONEY, NUMBER, PERCENT, BOOLEAN,
        /** PAN: always masked (BR-P9); never searched or sorted. */
        MASKED
    }

    /**
     * @param requires a second module the column's data comes from (null: only the report's module); the column is
     *                 left out, with its total, for users who can't read that module with the ALL scope
     * @param total    whether the column is summed over all filtered rows
     */
    public record Column(String key, String label, Type type, ModuleCode requires, boolean total) {

        public static Column text(String key, String label) {
            return new Column(key, label, Type.TEXT, null, false);
        }

        public static Column of(String key, String label, Type type) {
            return new Column(key, label, type, null, false);
        }

        public static Column money(String key, String label) {
            return new Column(key, label, Type.MONEY, null, true);
        }

        public Column requiring(ModuleCode module) {
            return new Column(key, label, type, module, total);
        }

        public Column totalled() {
            return new Column(key, label, type, requires, true);
        }

        public boolean sortable() {
            return type != Type.MASKED;
        }
    }

    public record Option(String value, String label) {
    }

    public Optional<Column> column(String key) {
        return columns.stream().filter(c -> c.key().equals(key)).findFirst();
    }
}
