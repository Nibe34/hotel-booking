package com.java.hotelbooking.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * What a job does when its service call fails: retry immediately while {@code refireCount < maxRetries},
 * then log the error and wait for the next scheduled run. The refire count is kept by Quartz per execution,
 * so the number of attempts is bounded and there is no endless loop.
 */
@Slf4j
final class JobFailurePolicy {

    private JobFailurePolicy() {
    }

    static JobExecutionException onFailure(JobExecutionContext context, Exception failure, int maxRetries) {
        String job = context.getJobDetail().getKey().toString();
        int retries = Math.max(0, maxRetries);
        int attempt = context.getRefireCount() + 1;
        if (context.getRefireCount() < retries) {
            log.warn("{} failed (attempt {} of {}), running it again: {}", job, attempt, retries + 1,
                    failure.getMessage());
            return new JobExecutionException(failure, true);
        }
        log.error("{} failed (attempt {} of {}), giving up until the next scheduled run", job, attempt, retries + 1,
                failure);
        return new JobExecutionException(failure, false);
    }
}
