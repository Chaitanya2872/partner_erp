package com.iotiq.partnererp.modules.audit.service;

/**
 * Audit action and entity-type names used in {@code audit_logs}.
 */
public final class AuditActions {

    public static final String ENTITY_USER = "USER";
    public static final String ENTITY_ROLE = "ROLE";
    public static final String ENTITY_PARTNER = "PARTNER";
    public static final String ENTITY_LEAD = "LEAD";
    public static final String ENTITY_PRODUCT = "PRODUCT";
    public static final String ENTITY_ESTIMATE = "ESTIMATE";

    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String LOGOUT = "LOGOUT";
    public static final String REFRESH_TOKEN_REUSED = "REFRESH_TOKEN_REUSED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String SUPER_ADMIN_BOOTSTRAPPED = "SUPER_ADMIN_BOOTSTRAPPED";

    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_ROLE_CHANGED = "USER_ROLE_CHANGED";
    public static final String USER_STATUS_CHANGED = "USER_STATUS_CHANGED";
    public static final String PERMISSIONS_CHANGED = "PERMISSIONS_CHANGED";
    public static final String ROLE_UPDATED = "ROLE_UPDATED";

    public static final String PARTNER_CREATED = "PARTNER_CREATED";
    public static final String PARTNER_UPDATED = "PARTNER_UPDATED";
    public static final String PARTNER_STATUS_CHANGED = "PARTNER_STATUS_CHANGED";
    public static final String PARTNER_TIER_REVIEWED = "PARTNER_TIER_REVIEWED";
    /** PAN checked or the check withdrawn (Phase 11); the PAN itself is recorded masked. */
    public static final String PARTNER_PAN_VERIFIED = "PARTNER_PAN_VERIFIED";

    public static final String LEAD_CREATED = "LEAD_CREATED";
    public static final String LEAD_UPDATED = "LEAD_UPDATED";
    public static final String LEAD_STAGE_CHANGED = "LEAD_STAGE_CHANGED";
    public static final String LEAD_ACKNOWLEDGED = "LEAD_ACKNOWLEDGED";
    public static final String LEAD_DELETED = "LEAD_DELETED";

    public static final String PRODUCT_CREATED = "PRODUCT_CREATED";
    public static final String PRODUCT_UPDATED = "PRODUCT_UPDATED";
    public static final String PRODUCT_STATUS_CHANGED = "PRODUCT_STATUS_CHANGED";
    public static final String PRODUCT_DELETED = "PRODUCT_DELETED";
    /** One product created or updated by a CSV import (old values null when created). */
    public static final String PRODUCT_IMPORTED = "PRODUCT_IMPORTED";
    /** Summary of a committed CSV import: counts only. */
    public static final String PRODUCT_IMPORT_COMPLETED = "PRODUCT_IMPORT_COMPLETED";

    /** Estimate audit entries hold partner-safe values only: never B2C names, B2C prices or the uplift. */
    public static final String ESTIMATE_CREATED = "ESTIMATE_CREATED";
    public static final String ESTIMATE_UPDATED = "ESTIMATE_UPDATED";
    public static final String ESTIMATE_STATUS_CHANGED = "ESTIMATE_STATUS_CHANGED";
    public static final String ESTIMATE_DELETED = "ESTIMATE_DELETED";

    /** Invoice audit entries hold partner-safe values only, like estimates. */
    public static final String ENTITY_INVOICE = "INVOICE";
    public static final String INVOICE_CREATED = "INVOICE_CREATED";
    /** A draft created from an estimate; the estimate gets its own ESTIMATE_STATUS_CHANGED entry. */
    public static final String INVOICE_CONVERTED = "INVOICE_CONVERTED";
    public static final String INVOICE_UPDATED = "INVOICE_UPDATED";
    public static final String INVOICE_TAX_CONFIRMED = "INVOICE_TAX_CONFIRMED";
    public static final String INVOICE_ISSUED = "INVOICE_ISSUED";
    public static final String INVOICE_CANCELLED = "INVOICE_CANCELLED";
    public static final String INVOICE_DRAFT_DELETED = "INVOICE_DRAFT_DELETED";

    public static final String ENTITY_PAYMENT = "PAYMENT";
    /** A receipt recorded against an issued invoice (Phase 10). References are recorded masked. */
    public static final String PAYMENT_RECORDED = "PAYMENT_RECORDED";
    /** A receipt reversed by a reversal entry, with the reason (Phase 10). Logged against the receipt. */
    public static final String PAYMENT_REVERSED = "PAYMENT_REVERSED";

    public static final String ENTITY_PAYOUT = "PAYOUT";
    /** A payout generated for a partner (Phase 11). Bank details are never recorded; at most the last four digits. */
    public static final String PAYOUT_CREATED = "PAYOUT_CREATED";
    public static final String PAYOUT_RECALCULATED = "PAYOUT_RECALCULATED";
    /** TDS of a draft or pending payout changed by hand, with the reason. */
    public static final String PAYOUT_TDS_ADJUSTED = "PAYOUT_TDS_ADJUSTED";
    public static final String PAYOUT_SUBMITTED = "PAYOUT_SUBMITTED";
    public static final String PAYOUT_APPROVED = "PAYOUT_APPROVED";
    public static final String PAYOUT_REJECTED = "PAYOUT_REJECTED";
    public static final String PAYOUT_CANCELLED = "PAYOUT_CANCELLED";
    public static final String PAYOUT_PROCESSING = "PAYOUT_PROCESSING";
    public static final String PAYOUT_PAID = "PAYOUT_PAID";
    public static final String PAYOUT_FAILED = "PAYOUT_FAILED";

    public static final String ENTITY_PARTNER_ADJUSTMENT = "PARTNER_ADJUSTMENT";
    /** A manual adjustment entered, or a reversal adjustment created when a paid receipt is reversed. */
    public static final String PARTNER_ADJUSTMENT_CREATED = "PARTNER_ADJUSTMENT_CREATED";
    public static final String PARTNER_ADJUSTMENT_APPROVED = "PARTNER_ADJUSTMENT_APPROVED";
    public static final String PARTNER_ADJUSTMENT_REJECTED = "PARTNER_ADJUSTMENT_REJECTED";

    public static final String ENTITY_TDS_CONFIGURATION = "TDS_CONFIGURATION";
    public static final String TDS_CONFIGURATION_UPDATED = "TDS_CONFIGURATION_UPDATED";
    public static final String TDS_CONFIGURATION_CONFIRMED = "TDS_CONFIGURATION_CONFIRMED";

    public static final String ENTITY_SETTINGS = "SETTINGS";
    /** Supplier GST registration (state, GSTIN, legal name, address). */
    public static final String GST_REGISTRATION_UPDATED = "GST_REGISTRATION_UPDATED";

    private AuditActions() {
    }
}
