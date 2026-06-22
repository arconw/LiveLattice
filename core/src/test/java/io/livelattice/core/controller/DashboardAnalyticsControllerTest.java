package io.livelattice.core.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.livelattice.core.exception.GlobalExceptionHandler;
import io.livelattice.core.model.dto.DashboardResponse;
import io.livelattice.core.service.DashboardService;
import io.livelattice.core.service.DataSourceService;
import io.livelattice.core.service.WidgetService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DashboardAnalyticsControllerTest {
    @Mock DashboardService dashboardService;
    @Mock WidgetService widgetService;
    @Mock DataSourceService dataSourceService;
    MockMvc mockMvc;
    String userId = "user-1";
    String workspaceId = UUID.randomUUID().toString();
    String dashboardId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            new DashboardController(dashboardService),
            new WidgetController(widgetService),
            new DataSourceController(dataSourceService),
            new DashboardDataController(widgetService)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void createDashboard_shouldReturnCreated() throws Exception {
        when(dashboardService.create(any(), eq(userId))).thenReturn(new DashboardResponse(
            dashboardId, workspaceId, "Analytics", null,
            Map.of("columns", 12, "widgets", List.of()),
            Map.of("type", "relative", "value", "24h"),
            0, false, UUID.randomUUID().toString(), Instant.now(), Instant.now()
        ));
        String body = "{\"workspaceId\":\"" + workspaceId + "\",\"title\":\"Analytics\",\"layout\":{\"columns\":12,\"widgets\":[]}}";
        mockMvc.perform(post("/dashboards").header("x-user-id", userId).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(dashboardId));
    }
}
