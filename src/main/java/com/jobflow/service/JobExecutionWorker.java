package com.jobflow.service;

import com.jobflow.service.executor.TaskContext;
import com.jobflow.service.executor.TaskHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JobExecutionWorker {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionWorker.class);
    private static final String SAFE_FAILURE_MESSAGE =
            "Task execution failed; consult application logs using the execution ID.";

    private final JobExecutionService executionService;
    private final TaskHandlerRegistry handlers;

    public JobExecutionWorker(JobExecutionService executionService, TaskHandlerRegistry handlers) {
        this.executionService = executionService;
        this.handlers = handlers;
    }

    public void run(long executionId) {
        try {
            JobExecutionService.RunSnapshot snapshot = executionService.startAttempt(executionId);
            if (snapshot == null) {
                return;
            }
            try {
                handlers.get(snapshot.taskType()).execute(snapshot.configuration(),
                        new TaskContext(snapshot.jobId(), executionId, snapshot.attemptNumber()));
            } catch (Exception exception) {
                log.error("Job task failed jobId={} executionId={} attempt={}",
                        snapshot.jobId(), executionId, snapshot.attemptNumber(), exception);
                executionService.finishFailure(executionId, snapshot.attemptNumber(),
                        snapshot.startedAt(), SAFE_FAILURE_MESSAGE);
                return;
            }
            executionService.finishSuccess(executionId, snapshot.attemptNumber(), snapshot.startedAt());
        } catch (RuntimeException exception) {
            log.error("Unexpected worker failure executionId={}", executionId, exception);
            throw exception;
        }
    }
}
