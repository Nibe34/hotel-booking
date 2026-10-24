package com.java.hotelbooking.configuration;

import com.java.hotelbooking.job.CheckInReminderJob;
import com.java.hotelbooking.job.ExpirePendingBookingsJob;
import org.junit.jupiter.api.Test;
import org.quartz.CronExpression;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/** No Spring context: the @Bean methods only build descriptions of jobs and triggers, so they can be inspected directly. */
class QuartzConfigTest {

    private final QuartzConfig config = new QuartzConfig();

    /** What Quartz hands to the job: the JobDetail's map overlaid with the trigger's map (trigger wins). */
    private JobDataMap merged(JobDetail detail, Trigger trigger) {
        JobDataMap merged = new JobDataMap();
        merged.putAll(detail.getJobDataMap());
        merged.putAll(trigger.getJobDataMap());
        return merged;
    }

    // ---------- names, groups, keys ----------

    @Test
    void jobDetails_haveNameGroupClassAndAreDurable() {
        JobDetail expire = config.expirePendingJobDetail();
        JobDetail reminder = config.checkInReminderJobDetail();

        assertThat(expire.getKey().getName()).isEqualTo("expirePendingBookingsJob");
        assertThat(expire.getKey().getGroup()).isEqualTo("hotel-booking");
        assertThat(expire.getJobClass()).isEqualTo(ExpirePendingBookingsJob.class);
        assertThat(expire.isDurable()).isTrue();
        assertThat(expire.requestsRecovery()).isTrue();

        assertThat(reminder.getKey().getName()).isEqualTo("checkInReminderJob");
        assertThat(reminder.getKey().getGroup()).isEqualTo("hotel-booking");
        assertThat(reminder.getJobClass()).isEqualTo(CheckInReminderJob.class);
        assertThat(reminder.isDurable()).isTrue();
    }

    @Test
    void everyTrigger_pointsToItsJobByJobKey_andHasNameAndGroup() {
        assertThat(config.expirePendingTrigger().getJobKey()).isEqualTo(QuartzConfig.EXPIRE_PENDING_JOB);
        assertThat(config.reminderDayBeforeTrigger().getJobKey()).isEqualTo(QuartzConfig.CHECK_IN_REMINDER_JOB);
        assertThat(config.reminderWeekBeforeTrigger().getJobKey()).isEqualTo(QuartzConfig.CHECK_IN_REMINDER_JOB);

        assertThat(List.of(config.expirePendingTrigger(), config.reminderDayBeforeTrigger(),
                config.reminderWeekBeforeTrigger()))
                .extracting(trigger -> trigger.getKey().toString())
                .containsExactly("hotel-booking.expirePendingEvery5Minutes", "hotel-booking.checkInReminderDayBefore",
                        "hotel-booking.checkInReminderWeekBefore");
    }

    // ---------- schedules and misfire instructions ----------

    @Test
    void expirePendingTrigger_every5Minutes_skipsMissedRuns() {
        SimpleTrigger trigger = (SimpleTrigger) config.expirePendingTrigger();

        assertThat(trigger.getRepeatInterval()).isEqualTo(5 * 60_000L);
        assertThat(trigger.getRepeatCount()).isEqualTo(SimpleTrigger.REPEAT_INDEFINITELY);
        assertThat(trigger.getMisfireInstruction())
                .isEqualTo(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT);
    }

    @Test
    void reminderDayBeforeTrigger_dailyAt18Kyiv_firesOnceNowAfterMisfire() {
        CronTrigger trigger = (CronTrigger) config.reminderDayBeforeTrigger();

        assertThat(trigger.getCronExpression()).isEqualTo("0 0 18 * * ?");
        assertThat(CronExpression.isValidExpression(trigger.getCronExpression())).isTrue();
        assertThat(trigger.getTimeZone().getID()).isEqualTo("Europe/Kyiv");
        assertThat(trigger.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
    }

    @Test
    void reminderWeekBeforeTrigger_dailyAt10Kyiv_doesNothingAfterMisfire() {
        CronTrigger trigger = (CronTrigger) config.reminderWeekBeforeTrigger();

        assertThat(trigger.getCronExpression()).isEqualTo("0 0 10 * * ?");
        assertThat(CronExpression.isValidExpression(trigger.getCronExpression())).isTrue();
        assertThat(trigger.getTimeZone().getID()).isEqualTo("Europe/Kyiv");
        assertThat(trigger.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
    }

    @Test
    void timeZone_unknownIdSilentlyBecomesGmt_thatIsWhyTheIdIsChecked() {
        assertThat(TimeZone.getTimeZone("Europe/Kiyv").getID()).isEqualTo("GMT"); // typo -> GMT, no error
        assertThat(QuartzConfig.ZONE.getID()).isEqualTo("Europe/Kyiv");
    }

    // ---------- JobDataMap: trigger values win, missing keys keep the job defaults ----------

    @Test
    void dayBeforeTrigger_overridesOnlyMaxRetries_restComesFromJob() {
        JobDataMap data = merged(config.checkInReminderJobDetail(), config.reminderDayBeforeTrigger());

        assertThat(data.getInt("maxRetries")).isEqualTo(1);   // trigger
        assertThat(data.getInt("daysBefore")).isEqualTo(1);   // job default
        assertThat(data.getInt("batchSize")).isEqualTo(100);  // job default
    }

    @Test
    void weekBeforeTrigger_overridesDaysBeforeAndBatchSize_keepsMaxRetriesOfJob() {
        JobDataMap data = merged(config.checkInReminderJobDetail(), config.reminderWeekBeforeTrigger());

        assertThat(data.getInt("daysBefore")).isEqualTo(7);   // trigger
        assertThat(data.getInt("batchSize")).isEqualTo(500);  // trigger
        assertThat(data.getInt("maxRetries")).isEqualTo(0);   // job default
    }

    @Test
    void expirePendingTrigger_hasNoOverrides_usesJobDefaults() {
        JobDataMap data = merged(config.expirePendingJobDetail(), config.expirePendingTrigger());

        assertThat(data.getLong("maxPendingMinutes")).isEqualTo(1440L);
        assertThat(data.getInt("batchSize")).isEqualTo(100);
        assertThat(data.getInt("maxRetries")).isEqualTo(2);
    }
}
