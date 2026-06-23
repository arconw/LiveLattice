package io.livelattice.notifications.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.livelattice.notifications.exception.GlobalExceptionHandler;
import io.livelattice.notifications.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationControllerValidationTest {

    private static final String SERVICE_ROLES = "service";
    private static final String USER_ROLES = "user";

    private final NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new NotificationController(notificationService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void rejectsNotificationWithoutRecipients() throws Exception {
        mockMvc.perform(post("/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-auth-roles", SERVICE_ROLES)
                .content("""
                    {
                      "recipientIds": [],
                      "type": "canvas.comment",
                      "channels": ["IN_APP"]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("validation_error"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void rejectsNotificationCreationWithoutServiceOrAdminRole() throws Exception {
        mockMvc.perform(post("/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-auth-roles", USER_ROLES)
                .content("""
                    {
                      "recipientIds": ["00000000-0000-0000-0000-000000000001"],
                      "type": "system.announcement",
                      "channels": ["IN_APP"]
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("forbidden"));

        verifyNoInteractions(notificationService);
    }
}
