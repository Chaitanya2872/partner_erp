package com.iotiq.partnererp.modules.products.dto;

import jakarta.validation.constraints.NotNull;

/** Activate ({@code true}) or deactivate ({@code false}) a product (D-17). */
public record ProductStatusRequest(@NotNull Boolean active, @NotNull Long version) {
}
