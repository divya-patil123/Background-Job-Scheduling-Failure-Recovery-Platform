package com.jobflow.service;

import com.jobflow.entity.AdminAlert;
import com.jobflow.entity.JobExecution;
import com.jobflow.entity.JobExecutionAttempt;
import com.jobflow.entity.ScheduledJob;
import com.jobflow.enums.AttemptStatus;
import com.jobflow.enums.ExecutionStatus;
import com.jobflow.exception.NotFoundException;
import com.jobflow.repository.AdminAlertRepository;
import com.jobflow.repository.JobExecutionAttemptRepository;
import com.jobflow.repository.JobExecutionRepository;
import com.jobflow.repository.ScheduledJobRepository;
import com.jobflow.service.notification.AlertNotificationService;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionService.class);

    private final JobExecutionRepository executionRepository;
    private final JobExecutionAttemptRepository attemptRepository;
    private final ScheduledJobRepository jobRepository;
    private final AdminAlertRepository alertRepository;
    private final com.jobflow.service.retry.RetryPolicy retryPolicy;
    private final AlertNotificationService alertNotificationService;

    public JobExecutionService(JobExecutionRepository executionRepository,
                               JobExecutionAttemptRepository attemptRepository,
                               ScheduledJobRepository jobRepository,
                               AdminAlertRepository alertRepository,
                               com.jobflow.service.retry.RetryPolicy retryPolicy,
                               AlertNotificationService alertNotificationService) {
        this.executionRepository = executionRepository;
        this.attemptRepository = attemptRepository;
        this.jobRepository = jobRepository;
        this.alertRepository = alertRepository;
        this.retryPolicy = retryPolicy;
        this.alertNotificationService = alertNotificationService;
    }

    @Transactional
    public void rejectBeforeRun(long executionId) {
        JobExecution execution = getExecution(executionId);
        if (execution.getStatus() != ExecutionStatus.QUEUED) {
            return;
        }
        Instant now = Instant.now();
        execution.markRejected(now, "Worker capacity is currently exhausted; trigger the job again later.");
        executionRepository.save(execution);
        recordFinalFailure(execution);
        jobRepository.release(execution.getJob().getId(), now);
        log.warn("Job execution rejected due to worker saturation jobId={} executionId={}",
                execution.getJob().getId(), executionId);
    }

    @Transactional
    public void recoverInterruptedExecution(long executionId) {
        JobExecution execution = executionRepository.findById(executionId).orElse(null);
        if (execution == null || execution.getStatus() != ExecutionStatus.RUNNING) {
            return;
        }

        java.util.List<JobExecutionAttempt> runningAttempts = attemptRepository
                .findByExecution_IdOrderByAttemptNumberAsc(executionId).stream()
                .filter(attempt -> attempt.getStatus() == AttemptStatus.RUNNING)
                .toList();
        Instant now = Instant.now();
        if (!runningAttempts.isEmpty()) {
            String message = "Execution was interrupted by application shutdown.";
            for (JobExecutionAttempt attempt : runningAttempts) {
                long duration = Math.max(0, Duration.between(attempt.getStartedAt(), now).toMillis());
                attempt.fail(now, duration, message);
                attemptRepository.save(attempt);
            }
            int latestAttemptNumber = runningAttempts.stream()
                    .mapToInt(JobExecutionAttempt::getAttemptNumber)
                    .max()
                    .orElseThrow();
            finishFailedAttempt(execution, latestAttemptNumber, now, message);
            return;
        }

        String message = "Execution was interrupted without a recoverable attempt record; automatic replay was skipped.";
        execution.failFinally(now, message);
        executionRepository.save(execution);
        recordFinalFailure(execution);
        jobRepository.release(execution.getJob().getId(), now);
        log.error("Interrupted execution has no running attempt; automatic replay skipped jobId={} executionId={}",
                execution.getJob().getId(), executionId);
    }

    @Transactional
    public RunSnapshot startAttempt(long executionId) {
        JobExecution execution = getExecution(executionId);
        if (execution.getStatus() != ExecutionStatus.QUEUED) {
            return null;
        }
        Instant now = Instant.now();
        execution.startAttempt(now);
        ScheduledJob job = execution.getJob();
        job.setExecutionLocked(true, now);
        JobExecutionAttempt attempt = new JobExecutionAttempt(execution, execution.getAttemptCount(), now);
        executionRepository.save(execution);
        attemptRepository.save(attempt);
        return new RunSnapshot(job.getId(), job.getTaskType(),
                java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(job.getConfiguration())),
                execution.getAttemptCount(), now);
    }

    @Transactional
    public void finishSuccess(long executionId, int attemptNumber, Instant started) {
        JobExecution execution = getExecution(executionId);
        JobExecutionAttempt attempt = getAttempt(executionId, attemptNumber);
        Instant ended = Instant.now();
        long duration = Math.max(0, Duration.between(started, ended).toMillis());
        attempt.succeed(ended, duration);
        execution.succeed(ended);
        execution.getJob().recordSuccess();
        attemptRepository.save(attempt);
        executionRepository.save(execution);
        jobRepository.save(execution.getJob());
        jobRepository.release(execution.getJob().getId(), ended);
        log.info("Job execution succeeded jobId={} executionId={} attempt={} durationMs={}",
                execution.getJob().getId(), executionId, attemptNumber, duration);
    }

    @Transactional
    public void finishFailure(long executionId, int attemptNumber, Instant started, String message) {
        JobExecution execution = getExecution(executionId);
        JobExecutionAttempt attempt = getAttempt(executionId, attemptNumber);
        Instant ended = Instant.now();
        long duration = Math.max(0, Duration.between(started, ended).toMillis());
        attempt.fail(ended, duration, message);
        attemptRepository.save(attempt);
        finishFailedAttempt(execution, attemptNumber, ended, message);
    }

    private void finishFailedAttempt(JobExecution execution, int attemptNumber,
                                     Instant ended, String message) {
        ScheduledJob job = execution.getJob();
        if (attemptNumber <= job.getMaxRetries()) {
            Duration delay = retryPolicy.delayForRetry(attemptNumber,
                    job.getInitialRetryDelayMs(), job.getMaxRetryDelayMs());
            Instant retryAt = ended.plus(delay);
            execution.retryAt(retryAt, message);
            executionRepository.save(execution);
            log.warn("Job execution scheduled for retry jobId={} executionId={} attempt={} retryAt={}",
                    job.getId(), execution.getId(), attemptNumber, retryAt);
            return;
        }
        execution.failFinally(ended, message);
        executionRepository.save(execution);
        recordFinalFailure(execution);
        jobRepository.release(job.getId(), ended);
        log.error("Job execution permanently failed jobId={} executionId={} attempts={}",
                job.getId(), execution.getId(), attemptNumber);
    }

    private void recordFinalFailure(JobExecution execution) {
        ScheduledJob job = execution.getJob();
        int previousFailures = job.getConsecutiveFailures();
        job.recordFailure();
        jobRepository.save(job);
        if (previousFailures < job.getFailureThreshold()
                && job.getConsecutiveFailures() >= job.getFailureThreshold()) {
            AdminAlert alert = alertRepository.save(new AdminAlert(job, execution,
                    "Job reached its consecutive failure threshold.", job.getConsecutiveFailures()));
            alertNotificationService.notifyAdministrator(alert);
        }
    }

    private JobExecution getExecution(long id) {
        return executionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Execution " + id + " was not found"));
    }

    private JobExecutionAttempt getAttempt(long executionId, int attemptNumber) {
        return attemptRepository.findByExecution_IdOrderByAttemptNumberAsc(executionId).stream()
                .filter(attempt -> attempt.getAttemptNumber() == attemptNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Execution attempt was not found"));
    }

    public record RunSnapshot(long jobId, com.jobflow.enums.TaskType taskType,
                              java.util.Map<String, Object> configuration, int attemptNumber, Instant startedAt) {
    }
}
