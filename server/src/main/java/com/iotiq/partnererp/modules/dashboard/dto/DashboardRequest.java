package com.iotiq.partnererp.modules.dashboard.dto;

import com.iotiq.partnererp.modules.reports.service.ReportPeriod;

import java.time.LocalDate;

/**
 * Period of a dashboard request: a preset, or a custom {@code from}/{@code to} (inclusive business dates). Without
 * either, the dashboard shows this financial year.
 */
public record DashboardRequest(ReportPeriod.Preset preset, LocalDate from, LocalDate to) {

    public ReportPeriod resolve(LocalDate today) {
        return ReportPeriod.resolve(preset, from, to, today, ReportPeriod.Preset.THIS_FY);
    }
}
