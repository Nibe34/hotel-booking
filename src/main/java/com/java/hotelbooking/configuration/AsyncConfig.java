package com.java.hotelbooking.configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;

import java.util.concurrent.Executor;

/**
 * All @Async methods run on one explicitly sized executor instead of Spring Boot's default one.
 * When both the pool and the queue are full the executor rejects new work (AbortPolicy ->
 * TaskRejectedException); every caller decides explicitly what to do with that.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties({AsyncProperties.class, SimulationProperties.class})
public class AsyncConfig implements AsyncConfigurer {

    private final AsyncProperties properties;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    public AsyncConfig(AsyncProperties properties, ObjectProvider<MeterRegistry> meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(properties.threadNamePrefix());
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        // the worker sees the same authenticated user as the caller (e.g. for the audit actor)
        executor.setTaskDecorator(DelegatingSecurityContextRunnable::new);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new LoggingAsyncUncaughtExceptionHandler(meterRegistry.getIfAvailable(() -> Metrics.globalRegistry));
    }
}
