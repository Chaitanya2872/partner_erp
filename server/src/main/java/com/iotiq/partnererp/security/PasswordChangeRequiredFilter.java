package com.iotiq.partnererp.security;

import com.iotiq.partnererp.common.constants.AppConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * While an account must change its password (first login, admin-set password), only the endpoints needed
 * to do so are allowed; everything else returns 403 PASSWORD_CHANGE_REQUIRED. Registered inside the
 * security chain only (not as a servlet filter bean).
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of(
            AppConstants.API_V1 + "/auth/me",
            AppConstants.API_V1 + "/auth/change-password",
            AppConstants.API_V1 + "/auth/logout",
            AppConstants.API_V1 + "/auth/refresh");

    private final JsonSecurityErrorHandler errorHandler;

    public PasswordChangeRequiredFilter(JsonSecurityErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean blocked = SecurityUtils.currentUser().map(AuthenticatedUser::mustChangePassword).orElse(false)
                && !ALLOWED.contains(request.getRequestURI().substring(request.getContextPath().length()));
        if (blocked) {
            errorHandler.write(response, HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED", "Password change required",
                    "Change your password before continuing.");
            return;
        }
        chain.doFilter(request, response);
    }
}
