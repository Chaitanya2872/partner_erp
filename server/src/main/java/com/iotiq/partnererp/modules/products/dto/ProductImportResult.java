package com.iotiq.partnererp.modules.products.dto;

import java.util.List;

/**
 * Outcome of a CSV import (D-18). With {@code dryRun} nothing is saved; otherwise valid rows were saved in one
 * transaction and rows with errors were skipped. {@code columns} lists the recognised columns; a column that is
 * absent, like a blank cell, leaves the stored value unchanged.
 */
public record ProductImportResult(boolean dryRun, int total, int created, int updated, int unchanged, int failed,
                                  List<String> columns, List<Row> rows) {

    public enum Outcome { CREATE, UPDATE, UNCHANGED, ERROR }

    /** {@code row} is the position among the data rows (1 = the first row after the header). */
    public record Row(int row, String sku, Outcome outcome, List<String> changes, List<String> errors,
                      List<String> warnings) {
    }
}
