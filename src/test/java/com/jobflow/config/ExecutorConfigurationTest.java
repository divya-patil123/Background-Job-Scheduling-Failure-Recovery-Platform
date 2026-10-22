package com.jobflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class ExecutorConfigurationTest {

    @Test
    void executesIndependentTasksOnMultipleWorkerThreads() throws InterruptedException {
        ThreadPoolTaskExecutor executor = new ExecutorConfiguration().jobExecutor(2, 2, 10, 1);
        executor.initialize();
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch releaseTasks = new CountDownLatch(1);
        Set<String> workerNames = ConcurrentHashMap.newKeySet();

        try {
            for (int i = 0; i < 2; i++) {
                executor.execute(() -> {
                    workerNames.add(Thread.currentThread().getName());
                    bothStarted.countDown();
                    try {
                        if (!releaseTasks.await(3, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting for the other worker");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Worker was interrupted", exception);
                    }
                });
            }

            assertTrue(bothStarted.await(5, TimeUnit.SECONDS));
            assertEquals(2, workerNames.size());
            assertNotEquals(workerNames.stream().findFirst().orElseThrow(),
                    workerNames.stream().skip(1).findFirst().orElseThrow());
        } finally {
            releaseTasks.countDown();
            executor.shutdown();
        }
    }
}
