package org.operaton.fitpub.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for asynchronous task execution.
 * Provides custom thread pools for different types of async operations.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfiguration implements AsyncConfigurer {

    /**
     * Custom thread pool executor for batch import operations.
     * Conservative limits prevent system overload when processing hundreds of files.
     *
     * Pool Configuration:
     * - Core pool size: 2 threads (allows 2 concurrent batch imports)
     * - Max pool size: 4 threads (scales up to 4 imports under heavy load)
     * - Queue capacity: 10 jobs (can queue up to 10 batch imports)
     * - Rejection policy: CallerRunsPolicy (blocks uploader if queue is full)
     *
     * @return configured thread pool executor for batch imports
     */
    @Bean(name = "batchImportExecutor")
    public Executor batchImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size - number of threads always kept alive
        executor.setCorePoolSize(2);

        // Maximum pool size - max number of threads that can be created
        executor.setMaxPoolSize(4);

        // Queue capacity - number of tasks that can be queued before rejection
        executor.setQueueCapacity(10);

        // Thread naming pattern for debugging
        executor.setThreadNamePrefix("batch-import-");

        // Keep idle threads alive for 60 seconds before terminating
        executor.setKeepAliveSeconds(60);

        // Allow core threads to timeout when idle
        executor.setAllowCoreThreadTimeOut(false);

        // Rejection handler: CallerRunsPolicy runs task in caller's thread if queue is full
        // This provides back-pressure and prevents overloading the system
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("Initialized batch import executor: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * Default executor for general async operations (e.g., activity summaries, notifications).
     * More generous thread pool for lightweight tasks.
     *
     * @return configured thread pool executor for general async tasks
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        log.info("Initialized default async executor: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * Exception handler for uncaught exceptions in async methods.
     * Logs the error with context about the failed method.
     *
     * @return async exception handler
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("Uncaught exception in async method '{}' with parameters {}",
                    method.getName(), params, throwable);
        };
    }
}
