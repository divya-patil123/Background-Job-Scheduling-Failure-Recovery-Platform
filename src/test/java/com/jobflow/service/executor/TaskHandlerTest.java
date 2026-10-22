package com.jobflow.service.executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskHandlerTest {

    @Test
    void idempotencyKeyIsStableAcrossAttemptsOfOneExecution() {
        assertEquals(new TaskContext(4, 12, 1).idempotencyKey(),
                new TaskContext(4, 12, 2).idempotencyKey());
        assertEquals("jobflow-execution-12", new TaskContext(4, 12, 2).idempotencyKey());
    }

    @Test
    void notificationRequiresMessage() {
        NotificationTaskHandler handler = new NotificationTaskHandler();
        assertDoesNotThrow(() -> handler.validate(Map.of("message", "Build completed")));
        assertThrows(IllegalArgumentException.class, () -> handler.validate(Map.of()));
    }

    @Test
    void reportRequiresName() {
        ReportTaskHandler handler = new ReportTaskHandler();
        assertDoesNotThrow(() -> handler.validate(Map.of("reportName", "Daily summary")));
        assertThrows(IllegalArgumentException.class, () -> handler.validate(Map.of("reportName", "")));
    }

    @Test
    void batchSizeMustBeWithinSupportedRange() {
        BatchProcessingTaskHandler handler = new BatchProcessingTaskHandler();
        assertDoesNotThrow(() -> handler.validate(Map.of("batchSize", 100)));
        assertThrows(IllegalArgumentException.class, () -> handler.validate(Map.of("batchSize", 0)));
        assertThrows(IllegalArgumentException.class, () -> handler.validate(Map.of("batchSize", 1.5)));
    }
}
