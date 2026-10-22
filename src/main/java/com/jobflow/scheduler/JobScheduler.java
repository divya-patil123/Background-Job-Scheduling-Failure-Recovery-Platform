package com.jobflow.scheduler;

import com.jobflow.entity.JobExecution;
import com.jobflow.enums.ExecutionStatus;
import com.jobflow.repository.JobExecutionRepository;
import com.jobflow.repository.ScheduledJobRepository;
import com.jobflow.service.ExecutionDispatchService;
import com.jobflow.service.JobExecutionService;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    private final ScheduledJobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final ExecutionDispatchService dispatchService;
    private final JobExecutionService executionService;
    private final int batchSize;
    private final AtomicBoolean initialized = new AtomicBoolean();

    public JobScheduler(ScheduledJobRepository jobRepository,
                        JobExecutionRepository executionRepository,
                        ExecutionDispatchService dispatchService,
                        JobExecutionService executionService,
                        @Value("${jobflow.scheduler.batch-size:100}") int batchSize) {
        this.jobRepository = jobRepository;
        this.executionRepository = executionRepository;
        this.dispatchService = dispatchService;
        this.executionService = executionService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${jobflow.scheduler.poll-interval:1000}")
    public void dispatchDueWork() {
        if (!initialized.get()) {
            return;
        }
        Instant now = Instant.now();
        jobRepository.findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(now,
                        PageRequest.of(0, batchSize))
                .forEach(job -> safelyDispatch(() -> dispatchService.dispatchScheduled(job.getId()),
                        "scheduled", job.getId()));
        executionRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                        ExecutionStatus.RETRYING, now)
                .forEach(execution -> safelyDispatch(() -> dispatchService.dispatchRetry(execution.getId()),
                        "retry", execution.getId()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterRestart() {
        List<Long> interruptedExecutionIds = collectIds(page -> executionRepository
                .findByStatusOrderByCreatedAtAscIdAsc(ExecutionStatus.RUNNING, page)
                .map(JobExecution::getId));
        interruptedExecutionIds.forEach(executionId -> {
            log.warn("Recovering interrupted execution executionId={}", executionId);
            executionService.recoverInterruptedExecution(executionId);
        });

        List<Long> queuedExecutionIds = collectIds(page -> executionRepository
                .findByStatusOrderByCreatedAtAscIdAsc(ExecutionStatus.QUEUED, page)
                .map(JobExecution::getId));
        queuedExecutionIds.forEach(executionId -> safelyDispatch(
                () -> dispatchService.dispatchQueued(executionId), "recovered queued", executionId));
        initialized.set(true);
    }

    private List<Long> collectIds(java.util.function.Function<PageRequest, Page<Long>> pageQuery) {
        java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
        int pageNumber = 0;
        Page<Long> page;
        do {
            page = pageQuery.apply(PageRequest.of(pageNumber++, batchSize));
            ids.addAll(page.getContent());
        } while (page.hasNext());
        return ids;
    }

    private void safelyDispatch(Runnable dispatch, String kind, long id) {
        try {
            dispatch.run();
        } catch (RuntimeException exception) {
            log.error("Unable to dispatch {} work id={}", kind, id, exception);
        }
    }
}
