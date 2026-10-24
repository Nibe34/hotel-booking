package com.java.hotelbooking.configuration;

import com.java.hotelbooking.job.CheckInReminderJob;
import com.java.hotelbooking.job.ExpirePendingBookingsJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * All jobs and triggers in one place. The schedule is stored in the QRTZ_* tables (JDBC job store);
 * with overwrite-existing-jobs these definitions replace the stored ones on every startup, so a new
 * deployment neither loses nor duplicates them.
 */
@Configuration
public class QuartzConfig {

    public static final String GROUP = "hotel-booking";

    public static final JobKey EXPIRE_PENDING_JOB = JobKey.jobKey("expirePendingBookingsJob", GROUP);
    public static final JobKey CHECK_IN_REMINDER_JOB = JobKey.jobKey("checkInReminderJob", GROUP);

    public static final TriggerKey EXPIRE_PENDING_TRIGGER = TriggerKey.triggerKey("expirePendingEvery5Minutes", GROUP);
    public static final TriggerKey REMINDER_DAY_BEFORE_TRIGGER = TriggerKey.triggerKey("checkInReminderDayBefore", GROUP);
    public static final TriggerKey REMINDER_WEEK_BEFORE_TRIGGER = TriggerKey.triggerKey("checkInReminderWeekBefore", GROUP);

    /** Hotels work in Kyiv time whatever the server's zone is. An unknown id would silently become GMT. */
    public static final TimeZone ZONE = TimeZone.getTimeZone("Europe/Kyiv");

    // ---------- expire pending bookings: one trigger ----------

    @Bean
    public JobDetail expirePendingJobDetail() {
        return JobBuilder.newJob(ExpirePendingBookingsJob.class)
                .withIdentity(EXPIRE_PENDING_JOB)
                .withDescription("Cancels PENDING bookings not confirmed within maxPendingMinutes or whose check-in has come")
                .usingJobData("maxPendingMinutes", 24L * 60)
                .usingJobData("batchSize", 100)
                .usingJobData("maxRetries", 2)
                .storeDurably()
                .requestRecovery() // idempotent, so it is safe to run again after a crash in the middle
                .build();
    }

    @Bean
    public Trigger expirePendingTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(EXPIRE_PENDING_JOB)
                .withIdentity(EXPIRE_PENDING_TRIGGER)
                .withDescription("Every 5 minutes")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(5)
                        .repeatForever()
                        // missed runs are not replayed: the next regular run handles everything that expired meanwhile
                        .withMisfireHandlingInstructionNextWithRemainingCount())
                .build();
    }

    // ---------- check-in reminders: one job, two triggers ----------

    @Bean
    public JobDetail checkInReminderJobDetail() {
        return JobBuilder.newJob(CheckInReminderJob.class)
                .withIdentity(CHECK_IN_REMINDER_JOB)
                .withDescription("E-mails guests of CONFIRMED bookings daysBefore days before check-in")
                .usingJobData("daysBefore", 1)
                .usingJobData("batchSize", 100)
                .usingJobData("maxRetries", 0)
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger reminderDayBeforeTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(CHECK_IN_REMINDER_JOB)
                .withIdentity(REMINDER_DAY_BEFORE_TRIGGER)
                .withDescription("Every day at 18:00 Kyiv time, for tomorrow's check-ins")
                .usingJobData("maxRetries", 1) // daysBefore and batchSize come from the job
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 18 * * ?")
                        .inTimeZone(ZONE)
                        // the guest arrives tomorrow: if 18:00 was missed, send the reminder as soon as possible
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }

    @Bean
    public Trigger reminderWeekBeforeTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(CHECK_IN_REMINDER_JOB)
                .withIdentity(REMINDER_WEEK_BEFORE_TRIGGER)
                .withDescription("Every day at 10:00 Kyiv time, for check-ins in a week")
                .usingJobData("daysBefore", 7)
                .usingJobData("batchSize", 500)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 10 * * ?")
                        .inTimeZone(ZONE)
                        // a courtesy reminder: a missed one is skipped, the day-before reminder still comes
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }
}
