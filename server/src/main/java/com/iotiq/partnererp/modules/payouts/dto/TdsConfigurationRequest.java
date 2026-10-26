package com.iotiq.partnererp.modules.payouts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
/**
 * The draft TDS configuration, as entered by Finance. Nothing is assumed: every value comes from this request.
 * {@code rateWithoutPanPercent} may be empty, in which case payouts to partners without a verified PAN are blocked.
 */
public record TdsConfigurationRequest(String sectionCode, String description, BigDecimal ratePercent,
                                      BigDecimal rateWithoutPanPercent, BigDecimal fyThreshold,
                                      LocalDate effectiveFrom, Long version) {
}
