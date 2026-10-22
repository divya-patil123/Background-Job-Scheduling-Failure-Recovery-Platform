package com.jobflow.dto;

import com.jobflow.enums.ScheduleType;
import com.jobflow.enums.TaskType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record JobUpsertRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @NotNull TaskType taskType,
        @NotNull ScheduleType scheduleType,
        @Positive @Max(31536000) Long intervalSeconds,
        @Size(max = 120) String cronExpression,
        @NotEmpty Map<String, Object> configuration,
        Boolean enabled,
        @Min(0) @Max(20) Integer maxRetries,
        @PositiveOrZero @Max(2592000000L) Long initialRetryDelayMs,
        @PositiveOrZero @Max(2592000000L) Long maxRetryDelayMs,
        @Min(1) @Max(1000) Integer failureThreshold) {

    @AssertTrue(message = "INTERVAL requires intervalSeconds and no cronExpression; CRON requires cronExpression and no intervalSeconds")
    public boolean isScheduleConfigurationValid() {
        if (scheduleType == ScheduleType.INTERVAL) {
            return intervalSeconds != null && cronExpression == null;
        }
        if (scheduleType == ScheduleType.CRON) {
            return cronExpression != null && !cronExpression.isBlank() && intervalSeconds == null;
        }
        return false;
    }

    @AssertTrue(message = "maxRetryDelayMs must be greater than or equal to initialRetryDelayMs")
    public boolean isRetryConfigurationValid() {
        return initialRetryDelayMs == null || maxRetryDelayMs == null || maxRetryDelayMs >= initialRetryDelayMs;
    }
}
