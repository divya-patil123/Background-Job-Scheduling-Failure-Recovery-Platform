package com.jobflow.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobflow.JobFlowApplication;
import com.jobflow.entity.JobExecution;
import com.jobflow.entity.JobExecutionAttempt;
import com.jobflow.entity.ScheduledJob;
import com.jobflow.enums.AttemptStatus;
import com.jobflow.enums.ExecutionStatus;
import com.jobflow.enums.ScheduleType;
import com.jobflow.enums.TaskType;
import com.jobflow.enums.TriggerType;
import com.jobflow.repository.JobExecutionAttemptRepository;
import com.jobflow.repository.JobExecutionRepository;
import com.jobflow.repository.ScheduledJobRepository;
import com.jobflow.service.ExecutionReservationService;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class JobSchedulerRecoveryIntegrationTest {

    private static final String DATABASE_URL =
            "jdbc:h2:mem:jobflow-recovery;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    @Test
    void applicationRestartRecoversPersistedExecutionsAndCompletesWork() throws Exception {
        long interruptedId;
        long queuedId;
        long retryId;
        long missingAttemptId;
        long missingAttemptJobId;

        try (ConfigurableApplicationContext firstRun = startApplication()) {
            ScheduledJobRepository jobs = firstRun.getBean(ScheduledJobRepository.class);
            JobExecutionRepository executions = firstRun.getBean(JobExecutionRepository.class);
            JobExecutionAttemptRepository attempts = firstRun.getBean(JobExecutionAttemptRepository.class);
            attempts.deleteAll();
            executions.deleteAll();
            jobs.deleteAll();

            Instant interruptedAt = Instant.now().minusSeconds(2);
            ScheduledJob interruptedJob = saveJob(jobs, "interrupted", 2);
            interruptedJob.setExecutionLocked(true, interruptedAt);
            jobs.saveAndFlush(interruptedJob);
            JobExecution interrupted = new JobExecution(interruptedJob, TriggerType.MANUAL);
            interrupted.startAttempt(interruptedAt);
            interrupted = executions.saveAndFlush(interrupted);
            interruptedId = interrupted.getId();
            attempts.saveAndFlush(new JobExecutionAttempt(interrupted, 1, interruptedAt));

            ScheduledJob queuedJob = saveJob(jobs, "queued", 0);
            JobExecution queued = executions.saveAndFlush(new JobExecution(queuedJob, TriggerType.MANUAL));
            queuedId = queued.getId();

            ScheduledJob retryJob = saveJob(jobs, "retry", 2);
            retryJob.setExecutionLocked(true, interruptedAt);
            jobs.saveAndFlush(retryJob);
            JobExecution retry = new JobExecution(retryJob, TriggerType.MANUAL);
            retry.startAttempt(interruptedAt);
            retry.retryAt(Instant.now().minusSeconds(1), "previous attempt failed");
            retry = executions.saveAndFlush(retry);
            retryId = retry.getId();
            JobExecutionAttempt failed = new JobExecutionAttempt(retry, 1, interruptedAt);
            failed.fail(interruptedAt.plusMillis(10), 10, "previous attempt failed");
            attempts.saveAndFlush(failed);

            ScheduledJob claimJob = saveJob(jobs, "retry-claim", 1);
            JobExecution claimable = new JobExecution(claimJob, TriggerType.MANUAL);
            claimable.startAttempt(interruptedAt);
            claimable.retryAt(Instant.now().minusSeconds(1), "previous attempt failed");
            claimable = executions.saveAndFlush(claimable);
            ExecutionReservationService reservations = firstRun.getBean(ExecutionReservationService.class);
            assertTrue(reservations.reserveRetry(claimable.getId()));
            assertFalse(reservations.reserveRetry(claimable.getId()));
        }

        try (ConfigurableApplicationContext restarted = startApplication()) {
            JobExecutionRepository executions = restarted.getBean(JobExecutionRepository.class);
            JobExecutionAttemptRepository attempts = restarted.getBean(JobExecutionAttemptRepository.class);
            ScheduledJobRepository jobs = restarted.getBean(ScheduledJobRepository.class);

            JobExecution recovered = executions.findById(interruptedId).orElseThrow();
            assertEquals(ExecutionStatus.RETRYING, recovered.getStatus());
            assertEquals(1, recovered.getAttemptCount());
            assertTrue(recovered.getNextRetryAt() != null);
            assertEquals(AttemptStatus.FAILED, attempts
                    .findByExecution_IdOrderByAttemptNumberAsc(interruptedId).get(0).getStatus());

            JobScheduler scheduler = restarted.getBean(JobScheduler.class);
            scheduler.dispatchDueWork();
            awaitStatus(executions, queuedId, ExecutionStatus.SUCCESS);
            JobExecution retryCompleted = awaitStatus(executions, retryId, ExecutionStatus.SUCCESS);
            assertEquals(2, retryCompleted.getAttemptCount());
            assertEquals(AttemptStatus.SUCCESS,
                    attempts.findByExecution_IdOrderByAttemptNumberAsc(retryId).get(1).getStatus());
            assertFalse(jobs.findById(retryCompleted.getJob().getId()).orElseThrow().isExecutionLocked());

            ScheduledJob missingAttemptJob = saveJob(jobs, "missing-attempt", 2);
            missingAttemptJob.setExecutionLocked(true, Instant.now());
            jobs.saveAndFlush(missingAttemptJob);
            JobExecution missingAttempt = new JobExecution(missingAttemptJob, TriggerType.MANUAL);
            missingAttempt.startAttempt(Instant.now().minusSeconds(1));
            missingAttempt = executions.saveAndFlush(missingAttempt);
            missingAttemptId = missingAttempt.getId();
            missingAttemptJobId = missingAttemptJob.getId();
        }

        try (ConfigurableApplicationContext restartedAgain = startApplication()) {
            JobExecution recovered = restartedAgain.getBean(JobExecutionRepository.class)
                    .findById(missingAttemptId).orElseThrow();
            assertEquals(ExecutionStatus.FAILED, recovered.getStatus());
            assertTrue(recovered.getErrorMessage().contains("automatic replay was skipped"));
            assertFalse(restartedAgain.getBean(ScheduledJobRepository.class)
                    .findById(missingAttemptJobId).orElseThrow().isExecutionLocked());
        }
    }

    private static ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(JobFlowApplication.class)
                .web(WebApplicationType.SERVLET)
                .run("--spring.datasource.url=" + DATABASE_URL,
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=update",
                        "--server.port=0",
                        "--jobflow.scheduler.batch-size=10",
                        "--jobflow.scheduler.poll-interval=3600000");
    }

    private static ScheduledJob saveJob(ScheduledJobRepository jobs, String name, int maxRetries) {
        return jobs.saveAndFlush(new ScheduledJob(name, null, TaskType.NOTIFICATION, ScheduleType.INTERVAL,
                60L, null, Map.of("message", "recovery test"), true, maxRetries,
                100, 1000, 3, Instant.now().plusSeconds(3600)));
    }

    private static JobExecution awaitStatus(JobExecutionRepository executions, long executionId,
                                            ExecutionStatus expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        JobExecution execution;
        do {
            execution = executions.findById(executionId).orElseThrow();
            if (execution.getStatus() == expected) {
                return execution;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        assertEquals(expected, execution.getStatus());
        return execution;
    }
}
