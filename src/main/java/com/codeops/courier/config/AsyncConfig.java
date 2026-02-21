package com.codeops.courier.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configures the asynchronous task execution infrastructure for CodeOps-Courier.
 *
 * <p>Provides a {@link ThreadPoolTaskExecutor} with a core pool of 5 threads, a maximum of 10,
 * and a queue capacity of 100. Threads are named with the {@code courier-async-} prefix.
 * When the queue is full, the {@link ThreadPoolExecutor.CallerRunsPolicy} is used to run
 * tasks on the calling thread rather than rejecting them.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Creates and initializes the thread pool executor used for all {@code @Async} method
     * invocations in the application.
     *
     * @return the configured and initialized {@link Executor}
     */
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("courier-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Provides an exception handler that logs uncaught exceptions thrown by {@code @Async} methods
     * at ERROR level, including the method name, exception message, and full stack trace.
     *
     * @return the {@link AsyncUncaughtExceptionHandler} for logging async failures
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("Async method {} threw exception: {}", method.getName(), throwable.getMessage(), throwable);
        };
    }
}
