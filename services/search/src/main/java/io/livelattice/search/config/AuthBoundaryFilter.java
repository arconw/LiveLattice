package io.livelattice.search.config;

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
        String subject = request.getHeader("x-auth-subject");
        String email = request.getHeader("x-auth-email");
        String displayName = request.getHeader("x-auth-display-name");
        if (blank(subject) || blank(email) || blank(displayName)) {
            unauthorized(response, "Missing authenticated user claims");
            return;
        }
        filterChain.doFilter(new AuthHeaderRequestWrapper(request, Map.of("x-user-id", subject)), response);
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
