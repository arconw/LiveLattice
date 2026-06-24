package io.livelattice.auditlog.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class AuthBoundaryFilterTest {

    @Test
    void allowsPublicHealthPath() throws Exception {
        AuthProperties props = new AuthProperties();
        AuthBoundaryFilter filter = new AuthBoundaryFilter(props);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/health");
        filter.doFilterInternal(request, response, chain);
        org.mockito.Mockito.verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsMissingInternalToken() throws Exception {
        AuthProperties props = new AuthProperties();
        props.setInternalSecret("secret");
        AuthBoundaryFilter filter = new AuthBoundaryFilter(props);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));
        when(request.getRequestURI()).thenReturn("/audit-log");
        when(request.getHeader("x-internal-auth-token")).thenReturn(null);
        filter.doFilterInternal(request, response, mock(FilterChain.class));
        org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals("{\"error\":\"unauthorized\",\"message\":\"Missing trusted gateway identity\"}", writer.toString());
    }

    @Test
    void rejectsMissingUserClaims() throws Exception {
        AuthProperties props = new AuthProperties();
        props.setInternalSecret("secret");
        AuthBoundaryFilter filter = new AuthBoundaryFilter(props);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));
        when(request.getRequestURI()).thenReturn("/audit-log");
        when(request.getHeader("x-internal-auth-token")).thenReturn("secret");
        when(request.getHeader("x-auth-subject")).thenReturn("");
        filter.doFilterInternal(request, response, mock(FilterChain.class));
        org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals("{\"error\":\"unauthorized\",\"message\":\"Missing authenticated user claims\"}", writer.toString());
    }

    @Test
    void allowsRequestWithValidTrustedIdentity() throws Exception {
        AuthProperties props = new AuthProperties();
        props.setInternalSecret("secret");
        AuthBoundaryFilter filter = new AuthBoundaryFilter(props);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/audit-log");
        when(request.getHeader("x-internal-auth-token")).thenReturn("secret");
        when(request.getHeader("x-auth-subject")).thenReturn("sub-1");
        when(request.getHeader("x-auth-email")).thenReturn("a@b.com");
        when(request.getHeader("x-auth-display-name")).thenReturn("User");
        filter.doFilterInternal(request, response, chain);
        org.mockito.Mockito.verify(chain).doFilter(request, response);
    }
}
