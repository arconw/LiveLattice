package io.livelattice.search.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.livelattice.search.config.AuthProperties;
import io.livelattice.search.exception.ForbiddenException;
import io.livelattice.search.opensearch.IndexEventProcessor;
import io.livelattice.search.opensearch.IndexManager;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReindexServiceTest {

    @Test
    void rejectsReindexWithoutTrustedAdminRole() {
        ReindexService service = new ReindexService(
            new AuthProperties(),
            mock(IndexManager.class),
            mock(PostgresReindexSource.class),
            mock(IndexEventProcessor.class)
        );

        assertThatThrownBy(() -> service.trigger(Map.of("x-auth-roles", "viewer")))
            .isInstanceOf(ForbiddenException.class);
    }
}
