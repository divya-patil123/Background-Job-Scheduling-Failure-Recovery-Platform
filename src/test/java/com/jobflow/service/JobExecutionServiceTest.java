package com.jobflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.jobflow.entity.AdminAlert;
import com.jobflow.entity.JobExecution;
import com.jobflow.entity.JobExecutionAttempt;
import com.jobflow.entity.ScheduledJob;
import com.jobflow.enums.ExecutionStatus;
import com.jobflow.enums.AttemptStatus;
import com.jobflow.enums.ScheduleType;
import com.jobflow.enums.TaskType;
import com.jobflow.repository.AdminAlertRepository;
import com.jobflow.repository.JobExecutionAttemptRepository;
import com.jobflow.repository.JobExecutionRepository;
import com.jobflow.repository.ScheduledJobRepository;
import com.jobflow.service.notification.AlertNotificationService;
import com.jobflow.service.retry.RetryPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JobExecutionServiceTest {

    private final JobExecutionRepository executionRepository = mock(JobExecutionRepository.class);
    private final JobExecutionAttemptRepository attemptRepository = mock(JobExecutionAttemptRepository.class);
    private final ScheduledJobRepository jobRepository = mock(ScheduledJobRepository.class);
    private final AdminAlertRepository alertRepository = mock(AdminAlertRepository.class);
    private final AlertNotificationService notifications = mock(AlertNotificationService.class);
    private final JobExecutionService service = new JobExecutionService(executionRepository, attemptRepository,
            jobRepository, alertRepository, new RetryPolicy(), notifications);

    @Test
    void failedAttemptIsRecordedAndQueuedForRetryUntilLimitIsReached() {
        ScheduledJob job = job(2, 3);
        JobExecution execution = execution(job);
        JobExecutionAttempt attempt = new JobExecutionAttempt(execution, 1, Instant.now().minusMillis(30));
        when(executionRepository.findById(10L)).thenReturn(Optional.of(execution));
        when(attemptRepository.findByExecution_IdOrderByAttemptNumberAsc(10L)).thenReturn(List.of(attempt));
        Instant failureStarted = Instant.now();

        service.finishFailure(10L, 1, attempt.getStartedAt(), "safe failure");

        assertEquals(ExecutionStatus.RETRYING, execution.getStatus());
        assertEquals(1, execution.getAttemptCount());
        assertTrue(execution.getNextRetryAt().isAfter(failureStarted));
        assertEquals(com.jobflow.enums.AttemptStatus.FAILED, attempt.getStatus());
        assertEquals("safe failure", attempt.getErrorMessage());
        verify(executionRepository).save(execution);
    }

    @Test
    void finalFailureIncrementsStreakAndCreatesThresholdAlert() {
        ScheduledJob job = job(0, 1);
        JobExecution execution = execution(job);
        JobExecutionAttempt attempt = new JobExecutionAttempt(execution, 1, Instant.now().minusMillis(10));
        when(executionRepository.findById(10L)).thenReturn(Optional.of(execution));
        when(attemptRepository.findByExecution_IdOrderByAttemptNumberAsc(10L)).thenReturn(List.of(attempt));
        when(alertRepository.save(any(AdminAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.finishFailure(10L, 1, attempt.getStartedAt(), "safe failure");

        assertEquals(ExecutionStatus.FAILED, execution.getStatus());
        assertEquals(1, job.getConsecutiveFailures());
        verify(alertRepository).save(any(AdminAlert.class));
        verify(notifications).notifyAdministrator(any(AdminAlert.class));
    }

    @Test
    void exhaustedRetryLimitFinishesExecutionInsteadOfSchedulingAnotherRetry() {
        ScheduledJob job = job(2, 3);
        JobExecution execution = execution(job);
        execution.startAttempt(Instant.now());
        execution.startAttempt(Instant.now());
        JobExecutionAttempt attempt = new JobExecutionAttempt(execution, 3, Instant.now().minusMillis(10));
        when(executionRepository.findById(10L)).thenReturn(Optional.of(execution));
        when(attemptRepository.findByExecution_IdOrderByAttemptNumberAsc(10L)).thenReturn(List.of(attempt));

        service.finishFailure(10L, 3, attempt.getStartedAt(), "safe failure");

        assertEquals(ExecutionStatus.FAILED, execution.getStatus());
        verify(jobRepository).release(1L, execution.getEndedAt());
    }

    @Test
    void successResetsConsecutiveFailuresAndReleasesJob() {
        ScheduledJob job = job(0, 3);
        job.recordFailure();
        JobExecution execution = execution(job);
        JobExecutionAttempt attempt = new JobExecutionAttempt(execution, 1, Instant.now().minusMillis(10));
        when(executionRepository.findById(10L)).thenReturn(Optional.of(execution));
        when(attemptRepository.findByExecution_IdOrderByAttemptNumberAsc(10L)).thenReturn(List.of(attempt));

        service.finishSuccess(10L, 1, attempt.getStartedAt());

        assertEquals(ExecutionStatus.SUCCESS, execution.getStatus());
        assertEquals(0, job.getConsecutiveFailures());
        verify(jobRepository).release(eq(1L), any(Instant.class));
    }

    @Test
    void interruptedRunningAttemptIsRecordedAndScheduledForRetry() {
        ScheduledJob job = job(1, 3);
        JobExecution execution = execution(job);
        job.setExecutionLocked(true, Instant.now());
        JobExecutionAttempt attempt = new JobExecutionAttempt(execution, 1, Instant.now().minusSeconds(1));
        ReflectionTestUtils.setField(attempt, "id", 20L);
        ReflectionTestUtils.setField(execution, "id", 10L);
        when(executionRepository.findById(10L)).thenReturn(Optional.of(execution));
        when(attemptRepository.findByExecution_IdOrderByAttemptNumberAsc(10L)).thenReturn(List.of(attempt));

        service.recoverInterruptedExecution(10L);

        assertEquals(AttemptStatus.FAILED, attempt.getStatus());
        assertEquals(ExecutionStatus.RETRYING, execution.getStatus());
        assertEquals("Execution was interrupted by application shutdown.", attempt.getErrorMessage());
        assertTrue(execution.getNextRetryAt().isAfter(Instant.now()));
        assertTrue(job.isExecutionLocked());
        verify(attemptRepository).save(attempt);
        verify(executionRepository).save(execution);
    }

    @Test
    void runningExecutionWithoutAttemptFailsSafelyAndReleasesTheJob() {
        ScheduledJob job = job(2, 3);
        JobExecution execution = execution(job);
        execution.startAttempt(Instant.now().minusSeconds(1));
        ReflectionTestUtils.setField(execution, "id", 10L);
        when(executionRepository.findById(10L)).thenReturn(Optional.of(execution));
        when(attemptRepository.findByExecution_IdOrderByAttemptNumberAsc(10L)).thenReturn(List.of());

        service.recoverInterruptedExecution(10L);

        assertEquals(ExecutionStatus.FAILED, execution.getStatus());
        assertTrue(execution.getErrorMessage().contains("automatic replay was skipped"));
        assertEquals(1, job.getConsecutiveFailures());
        verify(jobRepository).release(eq(1L), any(Instant.class));
    }

    @Test
    void alertsOnceWhenConsecutiveFinalFailuresCrossThreshold() {
        ScheduledJob job = job(0, 2);
        JobExecution first = execution(job);
        JobExecution second = execution(job);
        JobExecution third = execution(job);
        JobExecutionAttempt firstAttempt = new JobExecutionAttempt(first, 1, Instant.now().minusMillis(10));
        JobExecutionAttempt secondAttempt = new JobExecutionAttempt(second, 1, Instant.now().minusMillis(10));
        JobExecutionAttempt thirdAttempt = new JobExecutionAttempt(third, 1, Instant.now().minusMillis(10));
        when(executionRepository.findById(10L))
                .thenReturn(Optional.of(first), Optional.of(second), Optional.of(third));
        when(attemptRepository.findByExecution_IdOrderByAttemptNumberAsc(10L))
                .thenReturn(List.of(firstAttempt), List.of(secondAttempt), List.of(thirdAttempt));
        when(alertRepository.save(any(AdminAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.finishFailure(10L, 1, firstAttempt.getStartedAt(), "failure one");
        assertEquals(1, job.getConsecutiveFailures());
        service.finishFailure(10L, 1, secondAttempt.getStartedAt(), "failure two");
        assertEquals(2, job.getConsecutiveFailures());
        service.finishFailure(10L, 1, thirdAttempt.getStartedAt(), "failure three");
        assertEquals(3, job.getConsecutiveFailures());

        org.mockito.ArgumentCaptor<AdminAlert> alert = org.mockito.ArgumentCaptor.forClass(AdminAlert.class);
        verify(alertRepository, times(1)).save(alert.capture());
        assertEquals(2, alert.getValue().getFailureCount());
        verify(notifications, times(1)).notifyAdministrator(alert.getValue());
    }

    private ScheduledJob job(int maxRetries, int failureThreshold) {
        ScheduledJob job = new ScheduledJob("test", null, TaskType.NOTIFICATION, ScheduleType.INTERVAL,
                60L, null, Map.of("message", "hello"), true, maxRetries,
                250, 1000, failureThreshold, Instant.now().plusSeconds(60));
        ReflectionTestUtils.setField(job, "id", 1L);
        return job;
    }

    private JobExecution execution(ScheduledJob job) {
        JobExecution execution = new JobExecution(job, com.jobflow.enums.TriggerType.MANUAL);
        execution.startAttempt(Instant.now());
        ReflectionTestUtils.setField(execution, "id", 10L);
        return execution;
    }
}
