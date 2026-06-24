package io.livelattice.core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    void healthReturnsServiceStatus() {
        Map<String, Object> body = controller.health().getBody();
        assertEquals("UP", body.get("status"));
        assertEquals("core", body.get("service"));
        assertEquals("0.1.0", body.get("version"));
    }

    @Test
    void readyReturnsReadinessStatus() {
        Map<String, Object> body = controller.ready().getBody();
        assertEquals("UP", body.get("status"));
        Map<String, Object> checks = (Map<String, Object>) body.get("checks");
        assertEquals("healthy", ((Map<String, Object>) checks.get("database")).get("status"));
    }
}
