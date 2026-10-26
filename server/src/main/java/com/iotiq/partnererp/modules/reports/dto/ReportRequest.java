package com.iotiq.partnererp.modules.reports.dto;

import com.iotiq.partnererp.modules.reports.service.ReportPeriod;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filters of a report run or export. The period defaults to this financial year; {@code sort} is
 * {@code "column"} or {@code "column,asc|desc"} with a column key of the report.
 */
public record ReportRequest(ReportPeriod.Preset preset, LocalDate from, LocalDate to, String status, UUID partnerId,
                            String q, String sort, int page, int size) {

    public ReportPeriod resolve(LocalDate today) {
        return ReportPeriod.resolve(preset, from, to, today, ReportPeriod.Preset.THIS_FY);
    }
}
