package com.java.hotelbooking.job;

import com.java.hotelbooking.service.CheckInReminderService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * Sends check-in reminders {@code daysBefore} days ahead. The same job runs from two triggers (day before /
 * week before) that override {@code daysBefore}. Defaults below apply to keys a trigger does not set.
 */
@Slf4j
@Setter
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class CheckInReminderJob extends QuartzJobBean {

    private final CheckInReminderService reminderService;

    private int daysBefore = 1;
    private int batchSize = 100;
    private int maxRetries = 0;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            int sent = reminderService.sendReminders(daysBefore, batchSize);
            log.info("CheckInReminderJob: {} reminder(s) sent (daysBefore={}, batchSize={})", sent, daysBefore,
                    batchSize);
        } catch (RuntimeException e) {
            throw JobFailurePolicy.onFailure(context, e, maxRetries);
        }
    }
}
