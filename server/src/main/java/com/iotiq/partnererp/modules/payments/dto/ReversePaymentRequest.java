package com.iotiq.partnererp.modules.payments.dto;

/** Reverse a receipt (Payments reverse). The reason is required and kept on the reversal entry. */
public record ReversePaymentRequest(String reason) {
}
