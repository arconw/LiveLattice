package io.livelattice.backgroundjobs.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthBoundaryFilterTest {

    private final AuthProperties authProperties = new AuthProperties();

    AuthBoundaryFilterTest() {
        authProperties.setInternalSecret("secret");
    }

    @Test
    void publicHealthPathAllowedWithoutHeaders() throws Exception {
        AuthBoundaryFilter filter = new AuthBoundaryFilter(authProperties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);
        assertEquals(200, response.getStatus());
    }

    @Test
    void protectedPathRejectsMissingInternalToken() throws Exception {
        AuthBoundaryFilter filter = new AuthBoundaryFilter(authProperties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/jobs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);
        assertEquals(401, response.getStatus());
    }
}
