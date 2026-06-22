package io.livelattice.core.config;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.livelattice.core.exception.ForbiddenException;
import io.livelattice.core.service.ApiKeyService;
import io.livelattice.core.service.ApiKeyValidation;
import io.livelattice.core.service.AuthContext;
import io.livelattice.core.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthBoundaryFilterTest {

    @Test
    void apiKeyRequests_shouldLetDownstreamExceptionsPropagate() throws Exception {
        AuthProperties properties = new AuthProperties();
        UserService userService = mock(UserService.class);
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        AuthBoundaryFilter filter = new AuthBoundaryFilter(properties, userService, apiKeyService);
        ApiKeyValidation validation = new ApiKeyValidation("key-1", "workspace-1", "subject-1", "owner@example.com", "Owner", List.of("workspace:read"));
        when(apiKeyService.validate("valid-key")).thenReturn(validation);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/workspaces/workspace-1");
        request.addHeader("x-api-key", "valid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(ForbiddenException.class, () -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new ForbiddenException("denied");
        }));
        assertNull(AuthContext.apiKey());
        verify(apiKeyService).validate("valid-key");
    }
}
