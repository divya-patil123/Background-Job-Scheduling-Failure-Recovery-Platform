package com.jobflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobflow.dto.JobResponse;
import com.jobflow.dto.JobUpsertRequest;
import com.jobflow.entity.ScheduledJob;
import com.jobflow.enums.ScheduleType;
import com.jobflow.enums.TaskType;
import com.jobflow.exception.InvalidRequestException;
import com.jobflow.repository.JobExecutionRepository;
import com.jobflow.repository.ScheduledJobRepository;
import com.jobflow.service.executor.BatchProcessingTaskHandler;
import com.jobflow.service.executor.NotificationTaskHandler;
import com.jobflow.service.executor.ReportTaskHandler;
import com.jobflow.service.executor.TaskHandlerRegistry;
import com.jobflow.util.DtoMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.support.CronExpression;

class JobServiceTest {

    private final ScheduledJobRepository jobRepository = org.mockito.Mockito.mock(ScheduledJobRepository.class);
    private final JobExecutionRepository executionRepository = org.mockito.Mockito.mock(JobExecutionRepository.class);
    private final DtoMapper mapper = org.mockito.Mockito.mock(DtoMapper.class);
    private final JobService jobService = new JobService(jobRepository, executionRepository,
            new TaskHandlerRegistry(List.of(new NotificationTaskHandler(), new ReportTaskHandler(),
                    new BatchProcessingTaskHandler())), new SchedulingCalculator(), mapper);

    @Test
    void createsJobWithValidatedTaskConfigurationAndRetryDefaults() {
        JobUpsertRequest request = new JobUpsertRequest("  nightly-report  ", null, TaskType.REPORT,
                ScheduleType.INTERVAL, 60L, null, Map.of("reportName", "Daily summary"),
                null, null, null, null, null);
        JobResponse expected = new JobResponse(1L, "nightly-report", null, TaskType.REPORT,
                ScheduleType.INTERVAL, 60L, null, Map.of("reportName", "Daily summary"),
                true, 3, 1000, 60000, 3, 0, null, null, null);
        when(jobRepository.save(any(ScheduledJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(ScheduledJob.class))).thenReturn(expected);

        assertEquals(expected, jobService.create(request));

        ArgumentCaptor<ScheduledJob> saved = ArgumentCaptor.forClass(ScheduledJob.class);
        verify(jobRepository).save(saved.capture());
        assertEquals("nightly-report", saved.getValue().getName());
        assertEquals(3, saved.getValue().getMaxRetries());
        assertNotNull(saved.getValue().getNextRunAt());
    }

    @Test
    void rejectsConfigurationThatDoesNotMatchTaskType() {
        JobUpsertRequest request = new JobUpsertRequest("report", null, TaskType.REPORT,
                ScheduleType.INTERVAL, 60L, null, Map.of("message", "not a report"),
                null, null, null, null, null);

        assertThrows(InvalidRequestException.class, () -> jobService.create(request));
        verify(jobRepository, never()).save(any(ScheduledJob.class));
    }

    @Test
    void rejectsInvalidCronExpression() {
        JobUpsertRequest request = new JobUpsertRequest("report", null, TaskType.REPORT,
                ScheduleType.CRON, null, "not-cron", Map.of("reportName", "Daily"),
                null, null, null, null, null);

        assertThrows(InvalidRequestException.class, () -> jobService.create(request));
        verify(jobRepository, never()).save(any(ScheduledJob.class));
    }
}
