package com.jobflow.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobflow.entity.JobExecution;
import com.jobflow.entity.ScheduledJob;
import com.jobflow.enums.ExecutionStatus;
import com.jobflow.enums.ScheduleType;
import com.jobflow.enums.TaskType;
import com.jobflow.enums.TriggerType;
import com.jobflow.repository.JobExecutionRepository;
import com.jobflow.repository.ScheduledJobRepository;
import com.jobflow.service.ExecutionDispatchService;
import com.jobflow.service.JobExecutionService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class JobSchedulerTest {

    @Test
    void dispatchesDueJobsAndRetriesThroughExecutionDispatcher() {
        ScheduledJobRepository jobs = mock(ScheduledJobRepository.class);
        JobExecutionRepository executions = mock(JobExecutionRepository.class);
        List<String> dispatchCalls = new java.util.ArrayList<>();
        ExecutionDispatchService dispatch = recordingDispatcher(dispatchCalls);
        JobExecutionService executionService = recordingExecutionService(new java.util.ArrayList<>());
        JobScheduler scheduler = new JobScheduler(jobs, executions, dispatch, executionService, 100);
        ReflectionTestUtils.setField(scheduler, "initialized", new AtomicBoolean(true));

        ScheduledJob dueJob = new ScheduledJob("due", null, TaskType.REPORT, ScheduleType.INTERVAL,
                60L, null, Map.of("reportName", "Daily"), true,
                0, 1000, 1000, 3, Instant.now().minusSeconds(1));
        ReflectionTestUtils.setField(dueJob, "id", 11L);
        JobExecution retry = new JobExecution(dueJob, TriggerType.SCHEDULED);
        ReflectionTestUtils.setField(retry, "id", 22L);

        when(jobs.findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
                any(Instant.class), any(Pageable.class))).thenReturn(List.of(dueJob));
        when(executions.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(ExecutionStatus.RETRYING), any(Instant.class))).thenReturn(List.of(retry));

        scheduler.dispatchDueWork();

        org.junit.jupiter.api.Assertions.assertEquals(List.of("scheduled:11", "retry:22"), dispatchCalls);
    }

    @Test
    void recoversInterruptedExecutionsAndQueuedExecutionsAtStartup() {
        ScheduledJobRepository jobs = mock(ScheduledJobRepository.class);
        JobExecutionRepository executions = mock(JobExecutionRepository.class);
        List<String> dispatchCalls = new java.util.ArrayList<>();
        List<Long> recoveredExecutions = new java.util.ArrayList<>();
        ExecutionDispatchService dispatch = recordingDispatcher(dispatchCalls);
        JobExecutionService executionService = recordingExecutionService(recoveredExecutions);
        JobScheduler scheduler = new JobScheduler(jobs, executions, dispatch, executionService, 100);

        ScheduledJob job = new ScheduledJob("recovery-test", null, TaskType.REPORT, ScheduleType.INTERVAL,
                60L, null, Map.of("reportName", "Recovery"), true,
                0, 1000, 1000, 3, Instant.now().plusSeconds(60));
        ReflectionTestUtils.setField(job, "id", 1L);
        JobExecution running = new JobExecution(job, TriggerType.SCHEDULED);
        ReflectionTestUtils.setField(running, "id", 30L);
        JobExecution queued = new JobExecution(job, TriggerType.SCHEDULED);
        ReflectionTestUtils.setField(queued, "id", 41L);
        when(executions.findByStatusOrderByCreatedAtAscIdAsc(eq(ExecutionStatus.RUNNING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(running), PageRequest.of(0, 100), 1));
        when(executions.findByStatusOrderByCreatedAtAscIdAsc(eq(ExecutionStatus.QUEUED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(queued), PageRequest.of(0, 100), 1));

        scheduler.recoverAfterRestart();

        org.junit.jupiter.api.Assertions.assertEquals(List.of(30L), recoveredExecutions);
        org.junit.jupiter.api.Assertions.assertEquals(List.of("queued:41"), dispatchCalls);
        org.junit.jupiter.api.Assertions.assertTrue(
                ((AtomicBoolean) ReflectionTestUtils.getField(scheduler, "initialized")).get());
    }

    private static ExecutionDispatchService recordingDispatcher(List<String> calls) {
        return new ExecutionDispatchService(null, null, null, null, null) {
            @Override
            public void dispatchScheduled(long jobId) {
                calls.add("scheduled:" + jobId);
            }

            @Override
            public void dispatchRetry(long executionId) {
                calls.add("retry:" + executionId);
            }

            @Override
            public void dispatchQueued(long executionId) {
                calls.add("queued:" + executionId);
            }
        };
    }

    private static JobExecutionService recordingExecutionService(List<Long> recovered) {
        return new JobExecutionService(null, null, null, null, null, null) {
            @Override
            public void recoverInterruptedExecution(long executionId) {
                recovered.add(executionId);
            }
        };
    }
}
