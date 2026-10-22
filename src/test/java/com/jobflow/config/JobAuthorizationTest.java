package com.jobflow.config;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobflow.dto.ExecutionResponse;
import com.jobflow.enums.ExecutionStatus;
import com.jobflow.enums.TriggerType;
import com.jobflow.service.ExecutionDispatchService;
import com.jobflow.service.JobService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(com.jobflow.controller.JobController.class)
@Import(SecurityConfiguration.class)
@TestPropertySource(properties = {
        "jobflow.auth0.enabled=true",
        "jobflow.auth0.issuer-uri=https://auth.example.com/",
        "jobflow.auth0.audience=jobflow-api"
})
class JobAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    @MockBean
    private ExecutionDispatchService dispatchService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCanReadJobs() throws Exception {
        when(jobService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotTriggerJobs() throws Exception {
        mockMvc.perform(post("/api/jobs/7/trigger"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dispatchService);
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanTriggerJobs() throws Exception {
        when(dispatchService.triggerManually(7L)).thenReturn(new ExecutionResponse(42L, 7L,
                "scheduled-report", ExecutionStatus.QUEUED, TriggerType.MANUAL, 0,
                Instant.now(), null, null, null, null, null));

        mockMvc.perform(post("/api/jobs/7/trigger")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        verify(dispatchService).triggerManually(7L);
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotDeleteJobs() throws Exception {
        mockMvc.perform(delete("/api/jobs/7"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(jobService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCanDeleteJobs() throws Exception {
        mockMvc.perform(delete("/api/jobs/7"))
                .andExpect(status().isNoContent());

        verify(jobService).delete(7L);
    }
}
