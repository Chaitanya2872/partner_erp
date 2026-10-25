package com.iotiq.partnererp.unit.security;

import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.config.SecurityProperties;
import com.iotiq.partnererp.modules.auth.entity.DataScope;
import com.iotiq.partnererp.modules.auth.service.PasswordPolicy;
import com.iotiq.partnererp.modules.auth.service.RefreshTokenService;
import com.iotiq.partnererp.security.EffectivePermissions;
import com.iotiq.partnererp.security.ModuleAccess;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EffectivePermissionsTest {

    private final EffectivePermissions permissions = new EffectivePermissions(false, Map.of(
            "LEADS", new ModuleAccess(true, false, false, false, DataScope.ALL),
            "PAYOUTS", new ModuleAccess(false, false, false, false, DataScope.ALL)),
            Set.of("PAYOUTS:MARK_PAYOUT_PAID"));

    @Test
    void writeImpliesReadButNoSensitiveAction() {
        assertThat(permissions.canRead("LEADS")).isTrue();
        assertThat(permissions.canWrite("LEADS")).isTrue();
        assertThat(permissions.canPerform("LEADS", "DELETE")).isFalse();
        assertThat(permissions.canPerform("LEADS", "EXPORT")).isFalse();
        assertThat(permissions.canPerform("LEADS", "APPROVE")).isFalse();
        assertThat(permissions.authorities()).containsExactlyInAnyOrder("LEADS:READ", "LEADS:WRITE", "PAYOUTS:READ",
                "PAYOUTS:MARK_PAYOUT_PAID");
    }

    @Test
    void missingModuleMeansNoAccessAndActionsAreModuleBound() {
        assertThat(permissions.canRead("INVOICES")).isFalse();
        assertThat(permissions.canPerform("INVOICES", "READ")).isFalse();
        assertThat(permissions.canPerform("PAYOUTS", "MARK_PAYOUT_PAID")).isTrue();
        assertThat(permissions.canPerform("LEADS", "MARK_PAYOUT_PAID")).isFalse();
        assertThat(permissions.scope("INVOICES")).isEmpty();
    }

    @Test
    void passwordPolicy() {
        PasswordPolicy policy = new PasswordPolicy(new SecurityProperties(
                new SecurityProperties.Jwt("x".repeat(32), "partner-erp", Duration.ofMinutes(15)),
                new SecurityProperties.RefreshToken(Duration.ofDays(7), "iotiq_rt", "/api/v1/auth", true, "Strict"),
                new SecurityProperties.Login(5, Duration.ofMinutes(15)),
                new SecurityProperties.Password(12, 128)));
        assertThatCode(() -> policy.validate("p", "Long-Enough-123", "a@b.c")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate("p", "Short-1", "a@b.c")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> policy.validate("p", "OnlyLettersHere", "a@b.c")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> policy.validate("p", "123456789012", "a@b.c")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> policy.validate("p", "user1@iotiq.example", "USER1@iotiq.example"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> policy.validate("p", "a1".repeat(65), "a@b.c")).isInstanceOf(ValidationException.class);
    }

    @Test
    void refreshTokensAreStoredAsSha256Hashes() {
        String hash = RefreshTokenService.hash("raw-token");
        assertThat(hash).hasSize(64).matches("[0-9a-f]+").isNotEqualTo("raw-token")
                .isEqualTo(RefreshTokenService.hash("raw-token"));
    }
}
