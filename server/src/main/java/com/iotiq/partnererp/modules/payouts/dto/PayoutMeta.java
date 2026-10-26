package com.iotiq.partnererp.modules.payouts.dto;

import java.util.List;
/** Payout statuses and the caller's payout actions. */
public record PayoutMeta(List<Option> statuses, List<Option> adjustmentStatuses, int maxReason, int maxNotes,
                         int maxReference, boolean tdsConfigured, String tdsMessage, boolean canWrite,
                         boolean canApprove, boolean canMarkPaid, boolean canExport, boolean canConfigureTds,
                         boolean canViewSensitive) {

    public record Option(String value, String label) {
    }
}
