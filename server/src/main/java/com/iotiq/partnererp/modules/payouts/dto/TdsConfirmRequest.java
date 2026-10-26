package com.iotiq.partnererp.modules.payouts.dto;
/** Confirm the draft TDS configuration (a second person), with a note recording Finance's confirmation. */
public record TdsConfirmRequest(String note, Long version) {
}
