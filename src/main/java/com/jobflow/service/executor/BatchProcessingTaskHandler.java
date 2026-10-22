package com.jobflow.service.executor;

import com.jobflow.enums.TaskType;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BatchProcessingTaskHandler implements JobTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(BatchProcessingTaskHandler.class);

    @Override
    public TaskType taskType() {
        return TaskType.BATCH_PROCESSING;
    }

    @Override
    public void validate(Map<String, Object> configuration) {
        Object batchSize = configuration.get("batchSize");
        if (!(batchSize instanceof Number number)
                || number.intValue() < 1
                || number.intValue() > 100_000
                || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException("BATCH_PROCESSING configuration requires integer batchSize from 1 to 100000");
        }
    }

    @Override
    public void execute(Map<String, Object> configuration, TaskContext context) {
        int batchSize = ((Number) configuration.get("batchSize")).intValue();
        log.info("Processed {} simulated records for jobId={} executionId={}",
                batchSize, context.jobId(), context.executionId());
    }
}
