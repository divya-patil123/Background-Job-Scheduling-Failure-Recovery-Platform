package com.jobflow.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobflow.entity.JobExecution;
import com.jobflow.entity.JobExecutionAttempt;
import com.jobflow.entity.ScheduledJob;
import com.jobflow.enums.AttemptStatus;
import com.jobflow.enums.ExecutionStatus;
import com.jobflow.enums.ScheduleType;
import com.jobflow.enums.TaskType;
import com.jobflow.enums.TriggerType;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MySqlPersistenceIT {

    @Autowired
    private ScheduledJobRepository jobRepository;

    @Autowired
    private JobExecutionRepository executionRepository;

    @Autowired
    private JobExecutionAttemptRepository attemptRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> createdJobIds = new ArrayList<>();

    @Test
    void persistsJsonExecutionHistoryAndAttemptsAndAllowsOnlyOneConcurrentClaim() throws Exception {
        assertMySqlConnection();
        ScheduledJob job = createJob();
        long jobId = job.getId();
        verifyJsonRoundTrip(jobId);
        verifyOnlyOneConcurrentManualClaim(jobId);
        verifyExecutionAndAttemptPersistence(jobId);
    }

    @AfterEach
    void removeTestRows() {
        for (long jobId : createdJobIds) {
            List<JobExecution> executions = executionRepository.findByJob_Id(jobId, PageRequest.of(0, 100))
                    .getContent();
            for (JobExecution execution : executions) {
                attemptRepository.deleteAll(
                        attemptRepository.findByExecution_IdOrderByAttemptNumberAsc(execution.getId()));
            }
            executionRepository.deleteAll(executions);
            jobRepository.deleteById(jobId);
        }
    }

    private void assertMySqlConnection() throws SQLException {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
        }
    }

    private ScheduledJob createJob() {
        ScheduledJob job = new ScheduledJob("mysql-it-" + UUID.randomUUID(), null,
                TaskType.REPORT, ScheduleType.INTERVAL, 3600L, null,
                Map.of("reportName", "MySQL integration verification"), true,
                1, 1000, 5000, 3, Instant.now().plusSeconds(3600));
        ScheduledJob saved = jobRepository.saveAndFlush(job);
        createdJobIds.add(saved.getId());
        return saved;
    }

    private void verifyJsonRoundTrip(long jobId) {
        ScheduledJob reloaded = jobRepository.findById(jobId).orElseThrow();
        assertEquals(Map.of("reportName", "MySQL integration verification"), reloaded.getConfiguration());
    }

    private void verifyOnlyOneConcurrentManualClaim(long jobId) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstClaim = workers.submit(
                    () -> claimInTransaction(jobId, ready, start));
            Future<Integer> secondClaim = workers.submit(
                    () -> claimInTransaction(jobId, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(1, firstClaim.get(10, TimeUnit.SECONDS) + secondClaim.get(10, TimeUnit.SECONDS));
            assertTrue(jobRepository.findById(jobId).orElseThrow().isExecutionLocked());
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    private int claimInTransaction(long jobId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start concurrent claims");
        }
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> jobRepository.claimManual(jobId, Instant.now()));
    }

    private void verifyExecutionAndAttemptPersistence(long jobId) {
        ScheduledJob job = jobRepository.findById(jobId).orElseThrow();
        JobExecution execution = new JobExecution(job, TriggerType.MANUAL);
        Instant firstStarted = Instant.now();
        execution.startAttempt(firstStarted);
        JobExecutionAttempt firstAttempt = new JobExecutionAttempt(execution, 1, firstStarted);
        firstAttempt.fail(firstStarted.plusMillis(10), 10, "simulated MySQL integration failure");

        Instant secondStarted = firstStarted.plusSeconds(1);
        execution.startAttempt(secondStarted);
        JobExecutionAttempt secondAttempt = new JobExecutionAttempt(execution, 2, secondStarted);
        secondAttempt.succeed(secondStarted.plusMillis(20), 20);
        execution.succeed(secondStarted.plusMillis(20));
        JobExecution savedExecution = executionRepository.saveAndFlush(execution);
        attemptRepository.saveAndFlush(firstAttempt);
        attemptRepository.saveAndFlush(secondAttempt);

        JobExecution reloadedExecution = executionRepository.findById(savedExecution.getId()).orElseThrow();
        List<JobExecutionAttempt> attempts = attemptRepository
                .findByExecution_IdOrderByAttemptNumberAsc(savedExecution.getId());

        assertEquals(ExecutionStatus.SUCCESS, reloadedExecution.getStatus());
        assertEquals(2, reloadedExecution.getAttemptCount());
        assertEquals(2, attempts.size());
        assertEquals(AttemptStatus.FAILED, attempts.get(0).getStatus());
        assertEquals(AttemptStatus.SUCCESS, attempts.get(1).getStatus());
        assertEquals("simulated MySQL integration failure", attempts.get(0).getErrorMessage());
        assertNotNull(attempts.get(0).getEndedAt());
    }
}
