package com.iotiq.partnererp.modules.payouts.dto;
/** Approve (note optional) or reject (note required) a manual adjustment. */
public record AdjustmentDecisionRequest(String note) {
}
