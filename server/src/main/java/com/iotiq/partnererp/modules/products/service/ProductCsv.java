package com.iotiq.partnererp.modules.products.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 CSV reader and writer matching the source behaviour (BUSINESS_RULES §5.1): quoted fields,
 * {@code ""} escapes, CRLF or LF line ends, empty rows skipped. On output, fields containing a quote, comma or line
 * break are quoted, and text that a spreadsheet would run as a formula is prefixed with an apostrophe.
 */
public final class ProductCsv {

    private ProductCsv() {
    }

    /** Rows of fields; a leading UTF-8 byte-order mark is ignored and rows whose fields are all blank are dropped. */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int start = !text.isEmpty() && text.charAt(0) == '﻿' ? 1 : 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                addRow(rows, row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            addRow(rows, row);
        }
        return rows;
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        if (row.stream().anyMatch(f -> !f.isBlank())) {
            rows.add(row);
        }
    }

    /** CSV text with CRLF line ends. */
    public static String write(List<String> header, List<List<String>> rows) {
        StringBuilder out = new StringBuilder();
        appendRow(out, header);
        rows.forEach(r -> appendRow(out, r));
        return out.toString();
    }

    private static void appendRow(StringBuilder out, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(fields.get(i)));
        }
        out.append("\r\n");
    }

    private static final java.util.regex.Pattern PLAIN_NUMBER = java.util.regex.Pattern.compile("-?\\d+(\\.\\d+)?");

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        String safe = isFormula(value) ? "'" + value : value;
        if (safe.contains("\"") || safe.contains(",") || safe.contains("\n") || safe.contains("\r")) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    /**
     * Text starting with = + - @ (or a tab/carriage return) would be evaluated by spreadsheet programs. A plain signed
     * number such as {@code -200.00} (a reversal amount) is left as it is: it can't call anything, and guarding it
     * would turn it into text.
     */
    static boolean isFormula(String value) {
        if (value.isEmpty() || PLAIN_NUMBER.matcher(value).matches()) {
            return false;
        }
        char c = value.charAt(0);
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r';
    }

    /** Reverses the export's formula guard: "'=x" reads back as "=x". */
    public static String unguard(String value) {
        if (value != null && value.length() > 1 && value.charAt(0) == '\'' && isFormula(value.substring(1))) {
            return value.substring(1);
        }
        return value;
    }
}
