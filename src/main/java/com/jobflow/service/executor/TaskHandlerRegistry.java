package com.jobflow.service.executor;

import com.jobflow.enums.TaskType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TaskHandlerRegistry {

    private final Map<TaskType, JobTaskHandler> handlers;

    public TaskHandlerRegistry(List<JobTaskHandler> handlers) {
        EnumMap<TaskType, JobTaskHandler> registry = new EnumMap<>(TaskType.class);
        for (JobTaskHandler handler : handlers) {
            if (registry.put(handler.taskType(), handler) != null) {
                throw new IllegalStateException("More than one handler registered for " + handler.taskType());
            }
        }
        this.handlers = Map.copyOf(registry);
    }

    public JobTaskHandler get(TaskType type) {
        JobTaskHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("Unsupported task type: " + type);
        }
        return handler;
    }

    public void validate(TaskType type, Map<String, Object> configuration) {
        get(type).validate(configuration);
    }
}
