package com.iotiq.partnererp.modules.products.dto;

/**
 * Either the partner-safe or the internal product view; which one a caller gets depends on permission.
 */
public sealed interface ProductView permits ProductResponse, InternalProductResponse {
}
