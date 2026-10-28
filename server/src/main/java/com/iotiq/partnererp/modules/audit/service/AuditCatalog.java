package com.iotiq.partnererp.modules.audit.service;

import java.util.List;
import java.util.Optional;

/**
 * The audit viewer's modules and the actions each one records, so the viewer can filter by module and list every
 * action (the list used to be kept by hand in the client). Every action belongs to exactly one module.
 */
public final class AuditCatalog {

    public record Module(String code, String label, List<String> actions) {
    }

    public static final List<Module> MODULES = List.of(
            new Module("AUTH", "Sign-in and security", List.of(AuditActions.LOGIN_SUCCEEDED, AuditActions.LOGIN_FAILED,
                    AuditActions.ACCOUNT_LOCKED, AuditActions.ACCOUNT_UNLOCKED, AuditActions.LOGOUT,
                    AuditActions.REFRESH_TOKEN_REUSED, AuditActions.PASSWORD_CHANGED, AuditActions.PASSWORD_RESET,
                    AuditActions.SUPER_ADMIN_BOOTSTRAPPED)),
            new Module("USERS", "Users and permissions", List.of(AuditActions.USER_CREATED, AuditActions.USER_UPDATED,
                    AuditActions.USER_ROLE_CHANGED, AuditActions.USER_STATUS_CHANGED, AuditActions.PERMISSIONS_CHANGED)),
            new Module("ROLES", "Roles", List.of(AuditActions.ROLE_UPDATED, AuditActions.ROLE_PERMISSIONS_CHANGED)),
            new Module("PARTNERS", "Partners", List.of(AuditActions.PARTNER_CREATED, AuditActions.PARTNER_UPDATED,
                    AuditActions.PARTNER_STATUS_CHANGED, AuditActions.PARTNER_TIER_REVIEWED,
                    AuditActions.PARTNER_PAN_VERIFIED)),
            new Module("LEADS", "Leads", List.of(AuditActions.LEAD_CREATED, AuditActions.LEAD_UPDATED,
                    AuditActions.LEAD_STAGE_CHANGED, AuditActions.LEAD_ACKNOWLEDGED, AuditActions.LEAD_DELETED)),
            new Module("PRODUCTS", "Products", List.of(AuditActions.PRODUCT_CREATED, AuditActions.PRODUCT_UPDATED,
                    AuditActions.PRODUCT_STATUS_CHANGED, AuditActions.PRODUCT_DELETED, AuditActions.PRODUCT_IMPORTED,
                    AuditActions.PRODUCT_IMPORT_COMPLETED)),
            new Module("ESTIMATES", "Estimates", List.of(AuditActions.ESTIMATE_CREATED, AuditActions.ESTIMATE_UPDATED,
                    AuditActions.ESTIMATE_STATUS_CHANGED, AuditActions.ESTIMATE_DELETED)),
            new Module("INVOICES", "Invoices", List.of(AuditActions.INVOICE_CREATED, AuditActions.INVOICE_CONVERTED,
                    AuditActions.INVOICE_UPDATED, AuditActions.INVOICE_TAX_CONFIRMED, AuditActions.INVOICE_ISSUED,
                    AuditActions.INVOICE_CANCELLED, AuditActions.INVOICE_DRAFT_DELETED)),
            new Module("PAYMENTS", "Payments", List.of(AuditActions.PAYMENT_RECORDED, AuditActions.PAYMENT_REVERSED)),
            new Module("PAYOUTS", "Partner payouts and adjustments", List.of(AuditActions.PAYOUT_CREATED,
                    AuditActions.PAYOUT_RECALCULATED, AuditActions.PAYOUT_TDS_ADJUSTED, AuditActions.PAYOUT_SUBMITTED,
                    AuditActions.PAYOUT_APPROVED, AuditActions.PAYOUT_REJECTED, AuditActions.PAYOUT_CANCELLED,
                    AuditActions.PAYOUT_PROCESSING, AuditActions.PAYOUT_PAID, AuditActions.PAYOUT_FAILED,
                    AuditActions.PARTNER_ADJUSTMENT_CREATED, AuditActions.PARTNER_ADJUSTMENT_APPROVED,
                    AuditActions.PARTNER_ADJUSTMENT_REJECTED)),
            new Module("TAX", "GST and TDS settings", List.of(AuditActions.GST_REGISTRATION_UPDATED,
                    AuditActions.TDS_CONFIGURATION_UPDATED, AuditActions.TDS_CONFIGURATION_CONFIRMED)),
            new Module("SETTINGS", "Company settings and tiers", List.of(AuditActions.COMPANY_SETTINGS_UPDATED,
                    AuditActions.COMMERCIAL_SETTINGS_UPDATED, AuditActions.PARTNER_TIER_UPDATED)),
            new Module("AUDIT", "Audit log", List.of(AuditActions.AUDIT_LOG_EXPORTED)));

    private AuditCatalog() {
    }

    public static Optional<Module> module(String code) {
        return MODULES.stream().filter(m -> m.code().equalsIgnoreCase(code)).findFirst();
    }

    public static Optional<Module> moduleOf(String action) {
        return MODULES.stream().filter(m -> m.actions().contains(action)).findFirst();
    }
}
