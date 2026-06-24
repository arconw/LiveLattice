package io.livelattice.backgroundjobs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.backgroundjobs.dto.CreateJobRequest;
import io.livelattice.backgroundjobs.service.DeadLetterManager;
import io.livelattice.backgroundjobs.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        JobService jobService = mock(JobService.class);
        DeadLetterManager deadLetterManager = mock(DeadLetterManager.class);
        JobController controller = new JobController(jobService, deadLetterManager);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void validationFailureForInvalidType() throws Exception {
        CreateJobRequest request = new CreateJobRequest();
        request.setType("INVALID_TYPE");
        mockMvc.perform(post("/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}
