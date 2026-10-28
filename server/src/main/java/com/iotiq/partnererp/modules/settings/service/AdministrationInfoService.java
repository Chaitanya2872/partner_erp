package com.iotiq.partnererp.modules.settings.service;

import com.iotiq.partnererp.config.SecurityProperties;
import com.iotiq.partnererp.modules.auth.entity.UserStatus;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.settings.dto.SettingResponse;
import com.iotiq.partnererp.modules.sequences.service.DocumentNumberService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only administration views (Phase 13): document numbering, the security policy in force and the financial
 * controls. None of these can be changed from the application: numbering formats are fixed in code, the security
 * policy comes from the deployment configuration ({@code app.security.*}) and the controls are enforced by services,
 * database constraints and triggers.
 */
@Service
public class AdministrationInfoService {

    private record Series(String prefix, String document, String allocatedWhen) {
    }

    private static final List<Series> SERIES = List.of(
            new Series(DocumentNumberService.LEAD, "Leads", "When the lead is created"),
            new Series(DocumentNumberService.ESTIMATE, "Estimates", "When the estimate is created"),
            new Series(DocumentNumberService.INVOICE, "Invoices", "When a draft is issued; never reused after cancellation"),
            new Series(DocumentNumberService.PAYMENT, "Payments", "When a receipt or a reversal is recorded"),
            new Series(DocumentNumberService.PAYOUT, "Partner payouts", "When the payout is generated"),
            new Series(DocumentNumberService.ADJUSTMENT, "Partner adjustments", "When the adjustment is entered or created by a reversal"));

    /** Kept next to the code that enforces each control; DECISIONS.md holds the reasoning. */
    private static final List<SettingResponse.Control> CONTROLS = List.of(
            new SettingResponse.Control("Partners", "Tier and programme changes, applying tier reviews",
                    "PARTNERS:APPROVE", "PartnerService, TierReviewService (stale previews rejected)", "P5-03, P5-09"),
            new SettingResponse.Control("Leads", "Acknowledging a registration locks the fee % and protection date",
                    "LEADS:APPROVE", "LeadService (row lock, once only)", "P6-05"),
            new SettingResponse.Control("Estimates and invoices", "A fee % other than the default needs a reason",
                    "PARTNERS:APPROVE", "EstimateService, InvoiceService", "P8-07"),
            new SettingResponse.Control("Invoices", "Issued invoices are immutable; numbers are allocated at issue and never reused",
                    "INVOICES:WRITE / DELETE (cancel)", "InvoiceService, database triggers", "Phase 9"),
            new SettingResponse.Control("Payments", "Reversal of a recorded receipt, with a reason",
                    "PAYMENTS:REVERSE_PAYMENT", "PaymentService, ledger triggers", "Phase 10"),
            new SettingResponse.Control("Payouts", "Approval by someone other than the creator, the submitter and the author of a manual TDS change",
                    "PAYOUTS:APPROVE", "PayoutService, ck_payouts_separation", "P11-09"),
            new SettingResponse.Control("Payouts", "Processing and paid are recorded by hand; amounts never change after approval",
                    "PAYOUTS:MARK_PAYOUT_PAID", "PayoutService, payouts_guard trigger", "Phase 11, L-2"),
            new SettingResponse.Control("Payouts", "A receipt reversed while the transfer is processing: Paid only with an explicit acknowledgement",
                    "PAYOUTS:MARK_PAYOUT_PAID", "PayoutService", "L-8 (pending decision; unchanged)"),
            new SettingResponse.Control("Adjustments", "Manual adjustments are approved or rejected by another user",
                    "PAYOUTS:APPROVE", "PartnerAdjustmentService", "P11-01"),
            new SettingResponse.Control("TDS", "A draft TDS configuration is confirmed by someone other than its editor; confirmed versions never change",
                    "SETTINGS:WRITE edits, PAYOUTS:APPROVE confirms", "TdsConfigurationService, tds_configurations guard", "Phase 11, L-1"),
            new SettingResponse.Control("Commercial settings", "Partner tiers and lead protection",
                    "SETTINGS:WRITE + SETTINGS:MANAGE_SETTINGS", "SettingService", "P3-06, Phase 13"),
            new SettingResponse.Control("Users", "Admins manage User accounts only; nobody changes their own role, status or permissions; the last active Super Admin stays",
                    "USER_MANAGEMENT:WRITE / MANAGE_USERS", "UserService", "Phase 3, Phase 13"),
            new SettingResponse.Control("Permissions", "Only a Super Admin changes permissions or role defaults; Super Admin access is implicit",
                    "USER_MANAGEMENT:MANAGE_PERMISSIONS, ROLE_MANAGEMENT:WRITE", "PermissionService, RolePermissionService", "Phase 3, Phase 13"),
            new SettingResponse.Control("Audit", "The audit trail is append-only and sensitive values are masked when written",
                    "AUDIT_LOGS:READ / EXPORT", "AuditService, audit_logs guard trigger (V20)", "Phase 13"));

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final SecurityProperties security;
    private final Clock clock;

    public AdministrationInfoService(NamedParameterJdbcTemplate jdbc, UserRepository userRepository,
                                     SecurityProperties security, Clock clock) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.security = security;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SettingResponse.NumberSeries> numbering() {
        int year = LocalDate.now(clock).getYear();
        Map<String, Integer> last = new HashMap<>();
        jdbc.query("SELECT prefix, last_value FROM document_sequences WHERE fy_tag = :tag",
                new MapSqlParameterSource("tag", String.valueOf(year)),
                rs -> {
                    last.put(rs.getString("prefix"), rs.getInt("last_value"));
                });
        return SERIES.stream().map(s -> new SettingResponse.NumberSeries(s.prefix(), s.document(),
                s.prefix() + "-YYYY-NNNNNN", DocumentNumberService.format(s.prefix(), year, 1), s.allocatedWhen(), year,
                last.getOrDefault(s.prefix(), 0), DocumentNumberService.MAX)).toList();
    }

    @Transactional(readOnly = true)
    public SettingResponse.Security security() {
        SecurityProperties.Password p = security.password();
        SecurityProperties.Login l = security.login();
        return new SettingResponse.Security(
                new SettingResponse.PasswordPolicy(p.minLength(), p.maxLength(), List.of(
                        p.minLength() + " to " + p.maxLength() + " characters",
                        "At least one letter and one digit",
                        "Not the account's email address",
                        "New accounts and reset passwords must be changed at the next sign-in",
                        "A password change signs the account out everywhere else")),
                new SettingResponse.Lockout(l.maxFailedAttempts(), l.lockoutDuration().toMinutes(),
                        "The lock ends by itself, or an administrator who manages the account can clear it."),
                new SettingResponse.Sessions(security.jwt().accessTokenTtl().toMinutes(), security.refreshToken().ttl().toDays(),
                        List.of("Access tokens are short-lived; permissions and status are re-read on every request",
                                "Refresh tokens rotate on every use; reuse of an old token revokes the whole session",
                                "Deactivation, password change and password reset revoke every session immediately",
                                "Lifetimes are set in the deployment configuration and can't be changed here")),
                userRepository.countByLockedUntilAfter(Instant.now(clock)),
                userRepository.countByMustChangePasswordTrueAndStatus(UserStatus.ACTIVE));
    }

    public List<SettingResponse.Control> controls() {
        return CONTROLS;
    }
}
