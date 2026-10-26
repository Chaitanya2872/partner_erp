package com.iotiq.partnererp.modules.estimates.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Options and defaults for estimate forms and filters (BR-E2).
 *
 * @param canOverrideFee the caller may set a fee other than the default (D-13: {@code PARTNERS:APPROVE})
 */
public record EstimateMeta(List<Option> statuses, List<Option> billTo, int validityDays, String terms,
                           BigDecimal defaultGstPercent, int maxLines, boolean canOverrideFee) {

    public record Option(String value, String label) {
    }
}
