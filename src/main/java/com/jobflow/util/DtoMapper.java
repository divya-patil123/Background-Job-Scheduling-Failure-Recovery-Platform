package com.jobflow.util;

import com.jobflow.dto.AlertResponse;
import com.jobflow.dto.AttemptResponse;
import com.jobflow.dto.ExecutionResponse;
import com.jobflow.dto.JobResponse;
import com.jobflow.entity.AdminAlert;
import com.jobflow.entity.JobExecution;
import com.jobflow.entity.JobExecutionAttempt;
import com.jobflow.entity.ScheduledJob;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DtoMapper {

    public JobResponse toResponse(ScheduledJob job) {
        return new JobResponse(job.getId(), job.getName(), job.getDescription(), job.getTaskType(),
                job.getScheduleType(), job.getIntervalSeconds(), job.getCronExpression(), job.getConfiguration(),
                job.isEnabled(), job.getMaxRetries(), job.getInitialRetryDelayMs(),
                job.getMaxRetryDelayMs(), job.getFailureThreshold(), job.getConsecutiveFailures(),
                job.getNextRunAt(), job.getCreatedAt(), job.getUpdatedAt());
    }

    public ExecutionResponse toResponse(JobExecution execution) {
        return new ExecutionResponse(execution.getId(), execution.getJob().getId(),
                execution.getJob().getName(), execution.getStatus(), execution.getTriggerType(),
                execution.getAttemptCount(), execution.getCreatedAt(), execution.getStartedAt(),
                execution.getEndedAt(), execution.getDurationMs(), execution.getNextRetryAt(),
                execution.getErrorMessage());
    }

    public AttemptResponse toResponse(JobExecutionAttempt attempt) {
        return new AttemptResponse(attempt.getId(), attempt.getAttemptNumber(), attempt.getStatus(),
                attempt.getStartedAt(), attempt.getEndedAt(), attempt.getDurationMs(), attempt.getErrorMessage());
    }

    public AlertResponse toResponse(AdminAlert alert) {
        return new AlertResponse(alert.getId(), alert.getJob().getId(), alert.getJob().getName(),
                alert.getExecution().getId(), alert.getMessage(), alert.getFailureCount(),
                alert.isAcknowledged(), alert.getCreatedAt(), alert.getAcknowledgedAt());
    }
}
