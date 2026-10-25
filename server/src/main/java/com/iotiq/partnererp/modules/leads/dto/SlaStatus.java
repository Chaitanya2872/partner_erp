package com.iotiq.partnererp.modules.leads.dto;

/**
 * SLA clocks (BUSINESS_RULES §4.1) with the targets they were measured against, and the protection countdown.
 *
 * @param protectionDaysLeft days until protection ends (0 on its last day); null when not protected
 */
public record SlaStatus(Long ackHours, boolean ackBreach, Long estimateDays, boolean estimateBreach,
                        boolean hasEstimate, int ackTargetHours, int estimateTargetDays, Long protectionDaysLeft) {
}
