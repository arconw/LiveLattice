package io.livelattice.core.config;

import io.livelattice.core.service.ApiKeyService;
import io.livelattice.core.service.ApiKeyValidation;
import io.livelattice.core.service.AuthContext;
import io.livelattice.core.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthBoundaryFilter extends OncePerRequestFilter {

    private final AuthProperties authProperties;
    private final UserService userService;
    private final ApiKeyService apiKeyService;

    public AuthBoundaryFilter(AuthProperties authProperties, UserService userService, ApiKeyService apiKeyService) {
        this.authProperties = authProperties;
        this.userService = userService;
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isPublic(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        String apiKey = request.getHeader("x-api-key");
        if (apiKey != null && !apiKey.isBlank()) {
            validateApiKey(request, response, filterChain, apiKey);
            return;
        }
        if (!authProperties.getInternalSecret().equals(request.getHeader("x-internal-auth-token"))) {
            unauthorized(response, "Missing trusted gateway identity");
            return;
        }
        if (path.equals("/internal/auth/users/provision")) {
            filterChain.doFilter(request, response);
            return;
        }
        String subject = request.getHeader("x-auth-subject");
        String email = request.getHeader("x-auth-email");
        String displayName = request.getHeader("x-auth-display-name");
        if (blank(subject) || blank(email) || blank(displayName)) {
            unauthorized(response, "Missing authenticated user claims");
            return;
        }
        try {
            userService.requireBySubject(subject);
        } catch (RuntimeException ex) {
            unauthorized(response, "Unknown authenticated user");
            return;
        }
        filterChain.doFilter(new AuthHeaderRequestWrapper(request, Map.of("x-user-id", subject)), response);
    }

    private void validateApiKey(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain, String apiKey) throws IOException, ServletException {
        ApiKeyValidation validation;
        try {
            validation = apiKeyService.validate(apiKey);
        } catch (RuntimeException ex) {
            unauthorized(response, "Invalid API key");
            return;
        }
        AuthContext.setApiKey(validation);
        try {
            filterChain.doFilter(new AuthHeaderRequestWrapper(request, Map.of(
            "x-user-id", validation.userSubject(),
            "x-auth-subject", validation.userSubject(),
            "x-auth-email", validation.userEmail(),
            "x-auth-display-name", validation.userDisplayName(),
            "x-api-key-id", validation.apiKeyId(),
            "x-api-key-workspace-id", validation.workspaceId(),
            "x-api-key-permissions", String.join(",", validation.permissions())
            )), response);
        } finally {
            AuthContext.clear();
        }
    }

    private boolean isPublic(String path) {
        return path.equals("/health") || path.equals("/ready");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"" + message + "\"}");
    }
}
