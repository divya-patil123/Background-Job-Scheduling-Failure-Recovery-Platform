package com.jobflow.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobflow.enums.AttemptStatus;
import com.jobflow.enums.ExecutionStatus;
import com.jobflow.entity.ScheduledJob;
import com.jobflow.entity.JobExecution;
import com.jobflow.entity.JobExecutionAttempt;
import com.jobflow.enums.ScheduleType;
import com.jobflow.enums.TaskType;
import com.jobflow.enums.TriggerType;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jobflow-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ScheduledJobRepositoryTest {

    @Autowired
    private ScheduledJobRepository repository;

    @Autowired
    private JobExecutionRepository executionRepository;

    @Autowired
    private JobExecutionAttemptRepository attemptRepository;

    @Test
    void persistsJsonConfigurationAndFindsDueEnabledJob() {
        ScheduledJob job = new ScheduledJob("repository-test", "test job", TaskType.NOTIFICATION,
                ScheduleType.INTERVAL, 60L, null, Map.of("message", "persisted"),
                true, 1, 1000, 5000, 3, Instant.now().minusSeconds(1));

        ScheduledJob saved = repository.saveAndFlush(job);

        assertEquals(Map.of("message", "persisted"), repository.findById(saved.getId()).orElseThrow()
                .getConfiguration());
        assertEquals(1, repository.findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
                Instant.now(), PageRequest.of(0, 10)).size());
    }

    @Test
    void databaseClaimPreventsTwoConcurrentManualDispatches() {
        ScheduledJob job = new ScheduledJob("claim-test", null, TaskType.REPORT,
                ScheduleType.INTERVAL, 30L, null, Map.of("reportName", "Claim test"),
                true, 0, 1000, 1000, 3, Instant.now().plusSeconds(30));
        ScheduledJob saved = repository.saveAndFlush(job);
        Instant now = Instant.now();

        int firstClaim = repository.claimManual(saved.getId(), now);
        int secondClaim = repository.claimManual(saved.getId(), now.plusMillis(1));

        assertEquals(1, firstClaim);
        assertEquals(0, secondClaim);
        assertTrue(repository.findById(saved.getId()).orElseThrow().isExecutionLocked());
    }

    @Test
    void rejectsDuplicateAttemptNumbersForOneExecution() {
        ScheduledJob job = repository.saveAndFlush(new ScheduledJob("attempt-unique-test", null,
                TaskType.REPORT, ScheduleType.INTERVAL, 60L, null, Map.of("reportName", "Unique"),
                true, 0, 1000, 1000, 3, Instant.now().plusSeconds(60)));
        JobExecution execution = executionRepository.saveAndFlush(
                new JobExecution(job, TriggerType.MANUAL));
        Instant startedAt = Instant.now();
        attemptRepository.saveAndFlush(new JobExecutionAttempt(execution, 1, startedAt));
        attemptRepository.saveAndFlush(new JobExecutionAttempt(execution, 2, startedAt));

        assertThrows(DataIntegrityViolationException.class,
                () -> attemptRepository.saveAndFlush(new JobExecutionAttempt(execution, 1, startedAt)));
    }

    @Test
    void persistsExecutionHistoryAndOrderedAttemptDetails() {
        ScheduledJob job = repository.saveAndFlush(new ScheduledJob("history-test", null,
                TaskType.REPORT, ScheduleType.INTERVAL, 60L, null, Map.of("reportName", "History"),
                true, 1, 1000, 5000, 3, Instant.now().plusSeconds(60)));
        JobExecution execution = new JobExecution(job, TriggerType.MANUAL);
        Instant firstStart = Instant.now();
        execution.startAttempt(firstStart);
        JobExecutionAttempt firstAttempt = new JobExecutionAttempt(execution, 1, firstStart);
        firstAttempt.fail(firstStart.plusMillis(5), 5, "simulated failure");
        Instant secondStart = firstStart.plusSeconds(1);
        execution.startAttempt(secondStart);
        JobExecutionAttempt secondAttempt = new JobExecutionAttempt(execution, 2, secondStart);
        secondAttempt.succeed(secondStart.plusMillis(8), 8);
        execution.succeed(secondStart.plusMillis(8));
        JobExecution savedExecution = executionRepository.saveAndFlush(execution);
        attemptRepository.saveAndFlush(firstAttempt);
        attemptRepository.saveAndFlush(secondAttempt);

        JobExecution historyEntry = executionRepository.findByJob_Id(savedExecution.getJob().getId(),
                PageRequest.of(0, 10)).getContent().get(0);
        var persistedAttempts = attemptRepository.findByExecution_IdOrderByAttemptNumberAsc(savedExecution.getId());

        assertEquals(ExecutionStatus.SUCCESS, historyEntry.getStatus());
        assertEquals(2, historyEntry.getAttemptCount());
        assertEquals(2, persistedAttempts.size());
        assertEquals(AttemptStatus.FAILED, persistedAttempts.get(0).getStatus());
        assertEquals(AttemptStatus.SUCCESS, persistedAttempts.get(1).getStatus());
        assertEquals("simulated failure", persistedAttempts.get(0).getErrorMessage());
    }
}
