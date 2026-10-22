package com.jobflow.service;

import com.jobflow.dto.ExecutionResponse;
import com.jobflow.exception.ConflictException;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class ExecutionDispatchService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionDispatchService.class);

    private final ExecutionReservationService reservationService;
    private final JobExecutionService executionService;
    private final JobExecutionWorker executionWorker;
    private final ExecutionQueryService executionQueryService;
    private final ThreadPoolTaskExecutor jobExecutor;

    public ExecutionDispatchService(ExecutionReservationService reservationService,
                                    JobExecutionService executionService,
                                    JobExecutionWorker executionWorker,
                                    ExecutionQueryService executionQueryService,
                                    ThreadPoolTaskExecutor jobExecutor) {
        this.reservationService = reservationService;
        this.executionService = executionService;
        this.executionWorker = executionWorker;
        this.executionQueryService = executionQueryService;
        this.jobExecutor = jobExecutor;
    }

    public ExecutionResponse triggerManually(long jobId) {
        long executionId = reservationService.reserveManual(jobId);
        submit(executionId);
        return executionQueryService.findResponse(executionId);
    }

    public void dispatchScheduled(long jobId) {
        Long executionId = reservationService.reserveScheduled(jobId);
        if (executionId != null) {
            submit(executionId);
        }
    }

    public void dispatchRetry(long executionId) {
        if (reservationService.reserveRetry(executionId)) {
            submit(executionId);
        }
    }

    public void dispatchQueued(long executionId) {
        if (executionQueryService.isQueued(executionId)) {
            submit(executionId);
        }
    }

    private void submit(long executionId) {
        try {
            jobExecutor.execute(() -> executionWorker.run(executionId));
        } catch (RejectedExecutionException exception) {
            log.warn("Worker rejected job execution executionId={}", executionId, exception);
            executionService.rejectBeforeRun(executionId);
            throw new ConflictException("Worker capacity is exhausted; the job was not queued");
        }
    }
}
