package com.jobflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfiguration {

    @Bean(name = "jobExecutor")
    public ThreadPoolTaskExecutor jobExecutor(
            @Value("${jobflow.executor.core-pool-size:4}") int corePoolSize,
            @Value("${jobflow.executor.max-pool-size:8}") int maxPoolSize,
            @Value("${jobflow.executor.queue-capacity:100}") int queueCapacity,
            @Value("${jobflow.executor.shutdown-wait-seconds:30}") int shutdownWaitSeconds) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("jobflow-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(shutdownWaitSeconds);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
