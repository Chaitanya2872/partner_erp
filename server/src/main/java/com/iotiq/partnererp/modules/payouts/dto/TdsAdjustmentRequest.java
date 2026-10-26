package com.iotiq.partnererp.modules.payouts.dto;

import java.math.BigDecimal;
/** Replace the calculated TDS of a draft or pending payout, with the reason; the approver sees both amounts. */
public record TdsAdjustmentRequest(Long version, BigDecimal tdsAmount, String reason) {
}
