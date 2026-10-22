package com.jobflow.service.executor;

import com.jobflow.enums.TaskType;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationTaskHandler implements JobTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationTaskHandler.class);

    @Override
    public TaskType taskType() {
        return TaskType.NOTIFICATION;
    }

    @Override
    public void validate(Map<String, Object> configuration) {
        Object message = configuration.get("message");
        if (!(message instanceof String text) || text.isBlank() || text.length() > 2000) {
            throw new IllegalArgumentException("NOTIFICATION configuration requires a non-empty message up to 2000 characters");
        }
    }

    @Override
    public void execute(Map<String, Object> configuration, TaskContext context) {
        log.info("Simulated notification for jobId={} executionId={}",
                context.jobId(), context.executionId());
    }
}
