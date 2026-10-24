package com.java.hotelbooking.job;

import com.java.hotelbooking.service.BookingMaintenanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Our real job class on a real (in-memory) Quartz scheduler - no Spring, no database.
 * Shows what Quartz itself does with the job's choices: bounded refires and no concurrent runs.
 */
class QuartzSchedulerBehaviourTest {

    private final BookingMaintenanceService service = mock(BookingMaintenanceService.class);
    private Scheduler scheduler;

    @BeforeEach
    void startScheduler() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "test-" + UUID.randomUUID());
        properties.setProperty("org.quartz.threadPool.threadCount", "2");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        scheduler = new StdSchedulerFactory(properties).getScheduler();
        scheduler.setJobFactory((bundle, s) -> new ExpirePendingBookingsJob(service));
        scheduler.start();
    }

    @AfterEach
    void stopScheduler() throws Exception {
        scheduler.shutdown(true);
    }

    private JobKey addJob(int maxRetries) throws Exception {
        JobDetail job = JobBuilder.newJob(ExpirePendingBookingsJob.class)
                .withIdentity("expire-" + UUID.randomUUID(), "test")
                .usingJobData("maxRetries", maxRetries)
                .storeDurably()
                .build();
        scheduler.addJob(job, false);
        return job.getKey();
    }

    @Test
    void failingJob_isRunMaxRetriesPlusOneTimes_thenStops() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        when(service.expirePendingBookings(any(), anyInt())).thenAnswer(inv -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("database unavailable");
        });

        scheduler.triggerJob(addJob(2));

        await().atMost(Duration.ofSeconds(5)).until(() -> attempts.get() == 3);
        // and it stays at 3: no endless refire loop
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).until(() -> attempts.get() == 3);
    }

    @Test
    void secondRunDueWhileFirstIsRunning_waitsForIt() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();
        when(service.expirePendingBookings(any(), anyInt())).thenAnswer(inv -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            release.await(5, TimeUnit.SECONDS);
            running.decrementAndGet();
            finished.incrementAndGet();
            return 0;
        });
        JobKey job = addJob(0);

        scheduler.triggerJob(job);
        await().atMost(Duration.ofSeconds(5)).until(() -> running.get() == 1);
        scheduler.triggerJob(job); // a free worker thread exists, but the job is @DisallowConcurrentExecution

        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1)).until(() -> running.get() == 1);
        release.countDown();

        await().atMost(Duration.ofSeconds(5)).until(() -> finished.get() == 2);
        assertThat(maxRunning.get()).isEqualTo(1);
    }
}
