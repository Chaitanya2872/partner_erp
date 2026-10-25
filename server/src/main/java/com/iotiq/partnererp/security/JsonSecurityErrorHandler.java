package com.iotiq.partnererp.security;

import com.iotiq.partnererp.common.exception.GlobalExceptionHandler;
import com.iotiq.partnererp.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Writes 401/403 raised inside the security filter chain in the same envelope as controller errors
 * (exceptions thrown there never reach {@link GlobalExceptionHandler}).
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Unauthenticated",
                "Authentication is required.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden",
                "You do not have permission to perform this action.");
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String title, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.failure(GlobalExceptionHandler.error(status, code, title, detail, null));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
