package com.jobtracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Enables {@code @Async} and defines the pool that email sending runs on.
 *
 * <p><b>{@code @EnableAsync} is what makes {@code @Async} do anything at all.</b> Without it the
 * annotation is silently inert — annotated methods run on the caller's thread and nothing warns
 * you, the same failure mode as {@code flyway-core} without its starter.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Small on purpose. The service runs on a 512 MB Render instance where every thread costs
     * stack space, and the send rate is bounded by Brevo's free tier (300/day) anyway — this pool
     * exists to get slow network I/O off the request thread, not to achieve throughput.
     *
     * <p>{@code CallerRunsPolicy} means a full queue pushes the work back onto the calling thread
     * rather than dropping it. That makes the caller slow instead of silently losing an email,
     * which is the right trade here — and the queue is sized far above any realistic burst.
     */
    @Bean("emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("email-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Let in-flight sends finish on shutdown instead of killing the thread mid-request.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
