package com.iotiq.partnererp.modules.invoices.dto;

import java.util.UUID;

/** Create a draft invoice from an estimate. */
public record ConvertEstimateRequest(UUID estimateId) {
}
