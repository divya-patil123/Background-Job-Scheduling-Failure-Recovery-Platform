package com.jobflow.service.executor;

import com.jobflow.enums.TaskType;
import java.util.Map;

public interface JobTaskHandler {

    TaskType taskType();

    void validate(Map<String, Object> configuration);

    void execute(Map<String, Object> configuration, TaskContext context) throws Exception;
}
