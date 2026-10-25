package com.iotiq.partnererp.modules.auth.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.GlobalExceptionHandler;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.modules.auth.dto.AuthRequest;
import com.iotiq.partnererp.modules.auth.dto.AuthResponse;
import com.iotiq.partnererp.modules.auth.dto.ChangePasswordRequest;
import com.iotiq.partnererp.modules.auth.dto.MeResponse;
import com.iotiq.partnererp.modules.auth.service.AuthService;
import com.iotiq.partnererp.modules.auth.service.AuthService.LoginResult;
import com.iotiq.partnererp.modules.auth.service.ClientInfo;
import com.iotiq.partnererp.modules.auth.service.RefreshTokenRejectedException;
import com.iotiq.partnererp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Optional;

/**
 * Login, token refresh, logout, profile and password change. Access tokens are returned in the body
 * (kept in memory by the client); refresh tokens only ever travel in the HttpOnly cookie.
 */
@Tag(name = "Authentication")
@RestController
@RequestMapping(AppConstants.API_V1 + "/auth")
public class AuthController {

    /** Required on cookie-authenticated endpoints; cross-site forms cannot set custom headers. */
    static final String CSRF_HEADER = "X-Requested-With";

    private final AuthService authService;
    private final RefreshTokenCookies cookies;

    public AuthController(AuthService authService, RefreshTokenCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @Operation(summary = "Sign in with email and password")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request,
                                                           HttpServletRequest http) {
        LoginResult result = authService.login(request.email(), request.password(), ClientInfo.from(http));
        return withCookie(result);
    }

    @Operation(summary = "Rotate the refresh-token cookie and issue a new access token")
    @PostMapping("/refresh")
    public ResponseEntity<? extends ApiResponse<?>> refresh(HttpServletRequest http) {
        requireCsrfHeader(http);
        Optional<String> token = readCookie(http);
        if (token.isEmpty()) {
            return sessionExpired();
        }
        try {
            return withCookie(authService.refresh(token.get(), ClientInfo.from(http)));
        } catch (RefreshTokenRejectedException e) {
            return sessionExpired();
        }
    }

    @Operation(summary = "End the current session and clear the refresh-token cookie")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest http) {
        requireCsrfHeader(http);
        readCookie(http).ifPresent(authService::logout);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .body(ApiResponse.ok(null));
    }

    @Operation(summary = "The signed-in user's profile and effective permissions")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(authService.me(SecurityUtils.requireCurrentUser().id()));
    }

    @Operation(summary = "Change the signed-in user's password (signs out other sessions)")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                                    HttpServletRequest http) {
        LoginResult result = authService.changePassword(SecurityUtils.requireCurrentUser().id(),
                request.currentPassword(), request.newPassword(), ClientInfo.from(http));
        return withCookie(result);
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withCookie(LoginResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(result.refreshToken()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.ok(result.response()));
    }

    private ResponseEntity<ApiResponse<Void>> sessionExpired() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .body(ApiResponse.failure(GlobalExceptionHandler.error(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED",
                        "Session expired", "Your session has ended. Sign in again.", null)));
    }

    private Optional<String> readCookie(HttpServletRequest http) {
        Cookie[] all = http.getCookies();
        if (all == null) {
            return Optional.empty();
        }
        return Arrays.stream(all)
                .filter(c -> cookies.name().equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    private static void requireCsrfHeader(HttpServletRequest http) {
        if (http.getHeader(CSRF_HEADER) == null) {
            throw new BusinessException("CSRF_HEADER_MISSING", "The " + CSRF_HEADER + " header is required.",
                    HttpStatus.FORBIDDEN);
        }
    }
}
