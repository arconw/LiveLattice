package io.livelattice.search.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthBoundaryFilterTest {

    @Test
    void rejectsProtectedRequestWithoutTrustedGatewayHeader() throws Exception {
        AuthProperties properties = new AuthProperties();
        AuthBoundaryFilter filter = new AuthBoundaryFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/search");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void allowsHealthWithoutTrustedGatewayHeader() throws Exception {
        AuthProperties properties = new AuthProperties();
        AuthBoundaryFilter filter = new AuthBoundaryFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> response.setStatus(204));

        assertThat(response.getStatus()).isEqualTo(204);
    }
}
