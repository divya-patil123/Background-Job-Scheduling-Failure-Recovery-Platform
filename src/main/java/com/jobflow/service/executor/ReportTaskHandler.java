package com.jobflow.service.executor;

import com.jobflow.enums.TaskType;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReportTaskHandler implements JobTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(ReportTaskHandler.class);

    @Override
    public TaskType taskType() {
        return TaskType.REPORT;
    }

    @Override
    public void validate(Map<String, Object> configuration) {
        Object reportName = configuration.get("reportName");
        if (!(reportName instanceof String name) || name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("REPORT configuration requires a non-empty reportName up to 120 characters");
        }
    }

    @Override
    public void execute(Map<String, Object> configuration, TaskContext context) {
        log.info("Generated simulated report '{}' for jobId={} executionId={}",
                configuration.get("reportName"), context.jobId(), context.executionId());
    }
}
