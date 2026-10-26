package com.iotiq.partnererp.modules.payouts.dto;

import java.time.LocalDate;
import java.util.UUID;
/** Preview what a payout for the partner would contain; nothing is saved. */
public record PayoutPreviewRequest(UUID partnerId, LocalDate asOfDate) {
}
