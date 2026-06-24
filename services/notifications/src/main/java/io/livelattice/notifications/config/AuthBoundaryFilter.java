package io.livelattice.notifications.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthBoundaryFilter extends OncePerRequestFilter {

    private final AuthProperties properties;

    public AuthBoundaryFilter(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isPublic(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!properties.getInternalSecret().equals(request.getHeader("x-internal-auth-token"))) {
            unauthorized(response, "Missing trusted gateway identity");
            return;
        }
        if (blank(request.getHeader("x-user-id"))) {
            unauthorized(response, "Missing notification user identity");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPublic(String path) {
        return path.equals("/health") || path.equals("/ready") || path.startsWith("/actuator/");
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
