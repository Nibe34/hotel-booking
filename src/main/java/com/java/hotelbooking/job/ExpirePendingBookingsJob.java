package com.java.hotelbooking.job;

import com.java.hotelbooking.service.BookingMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.Duration;

/**
 * Cancels PENDING bookings that were not confirmed in time. Two runs at once could pick the same bookings,
 * so concurrent execution is disallowed: a run that is due while the previous one still works waits for it.
 * <p>
 * JobDataMap keys are bound to the setters by QuartzJobBean. A missing key - or a misspelled one, which Quartz
 * simply ignores - leaves the default below; a value of the wrong type fails the run.
 */
@Slf4j
@Setter
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class ExpirePendingBookingsJob extends QuartzJobBean {

    private final BookingMaintenanceService maintenanceService;

    private long maxPendingMinutes = 24 * 60;
    private int batchSize = 100;
    private int maxRetries = 2;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            int expired = maintenanceService.expirePendingBookings(Duration.ofMinutes(maxPendingMinutes), batchSize);
            log.info("ExpirePendingBookingsJob: {} booking(s) cancelled (maxPendingMinutes={}, batchSize={})",
                    expired, maxPendingMinutes, batchSize);
        } catch (RuntimeException e) {
            throw JobFailurePolicy.onFailure(context, e, maxRetries);
        }
    }
}
