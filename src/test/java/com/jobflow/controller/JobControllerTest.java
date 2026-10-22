package com.jobflow.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobflow.dto.ExecutionResponse;
import com.jobflow.dto.JobResponse;
import com.jobflow.enums.ExecutionStatus;
import com.jobflow.enums.ScheduleType;
import com.jobflow.enums.TaskType;
import com.jobflow.exception.GlobalExceptionHandler;
import com.jobflow.service.ExecutionDispatchService;
import com.jobflow.service.JobService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JobController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    @MockBean
    private ExecutionDispatchService dispatchService;

    @Test
    void invalidCreateRequestReturnsValidationDetails() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "taskType": "REPORT",
                                  "scheduleType": "INTERVAL",
                                  "intervalSeconds": 0,
                                  "configuration": {}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    void createJobReturnsCreatedResource() throws Exception {
        JobResponse response = jobResponse(true);
        when(jobService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJobRequest()))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Location", "/api/jobs/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.enabled").value(true));

        verify(jobService).create(any());
    }

    @Test
    void updateAndEnableEndpointsDelegateToJobService() throws Exception {
        when(jobService.update(eq(7L), any())).thenReturn(jobResponse(true));
        when(jobService.setEnabled(7L, false)).thenReturn(jobResponse(false));
        when(jobService.setEnabled(7L, true)).thenReturn(jobResponse(true));

        mockMvc.perform(put("/api/jobs/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJobRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("scheduled-report"));

        mockMvc.perform(patch("/api/jobs/7/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(patch("/api/jobs/7/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        verify(jobService).update(eq(7L), any());
        verify(jobService).setEnabled(7L, false);
        verify(jobService).setEnabled(7L, true);
    }

    @Test
    void deleteAndManualTriggerExposeExpectedHttpStatuses() throws Exception {
        when(dispatchService.triggerManually(7L)).thenReturn(new ExecutionResponse(42L, 7L,
                "scheduled-report", ExecutionStatus.QUEUED,
                com.jobflow.enums.TriggerType.MANUAL, 0, Instant.now(),
                null, null, null, null, null));

        mockMvc.perform(delete("/api/jobs/7"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/jobs/7/trigger"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.triggerType").value("MANUAL"));

        verify(jobService).delete(7L);
        verify(dispatchService).triggerManually(7L);
    }

    private String validJobRequest() {
        return """
                {
                  "name": "scheduled-report",
                  "taskType": "REPORT",
                  "scheduleType": "INTERVAL",
                  "intervalSeconds": 60,
                  "configuration": {"reportName": "Daily"}
                }
                """;
    }

    private JobResponse jobResponse(boolean enabled) {
        return new JobResponse(7L, "scheduled-report", null, TaskType.REPORT,
                ScheduleType.INTERVAL, 60L, null, Map.of("reportName", "Daily"),
                enabled, 3, 1000, 60000, 3, 0, Instant.now(), Instant.now(), Instant.now());
    }
}
