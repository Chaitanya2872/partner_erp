package com.iotiq.partnererp.modules.leads.dto;

import java.util.UUID;

/**
 * The referring partner as shown on a lead.
 */
public record PartnerRef(UUID id, String name) {
}
