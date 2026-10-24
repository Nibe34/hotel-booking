package com.java.hotelbooking.service;

import com.java.hotelbooking.configuration.QuartzConfig;
import com.java.hotelbooking.domain.dto.ScheduledTriggerResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledJobsServiceTest {

    @Mock
    private Scheduler scheduler;

    @InjectMocks
    private ScheduledJobsService service;

    private final QuartzConfig config = new QuartzConfig();

    @Test
    void runNow_triggersJobWithOverridesAsTriggerData() throws Exception {
        service.runNow(QuartzConfig.CHECK_IN_REMINDER_JOB, Map.of("daysBefore", 7));

        ArgumentCaptor<JobDataMap> data = ArgumentCaptor.forClass(JobDataMap.class);
        verify(scheduler).triggerJob(eq(QuartzConfig.CHECK_IN_REMINDER_JOB), data.capture());
        assertThat(data.getValue().getInt("daysBefore")).isEqualTo(7);
    }

    @Test
    void runNow_schedulerError_isReportedWithJobName() throws Exception {
        doThrow(new SchedulerException("down")).when(scheduler).triggerJob(any(), any());

        assertThatThrownBy(() -> service.runNow(QuartzConfig.EXPIRE_PENDING_JOB, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot trigger hotel-booking.expirePendingBookingsJob");
    }

    @Test
    void listTriggers_describesStoredTriggersSortedByName() throws Exception {
        Trigger weekBefore = config.reminderWeekBeforeTrigger();
        Trigger expire = config.expirePendingTrigger();
        when(scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(QuartzConfig.GROUP)))
                .thenReturn(Set.of(weekBefore.getKey(), expire.getKey()));
        when(scheduler.getTrigger(weekBefore.getKey())).thenReturn(weekBefore);
        when(scheduler.getTrigger(expire.getKey())).thenReturn(expire);
        when(scheduler.getTriggerState(any())).thenReturn(Trigger.TriggerState.NORMAL);

        var result = service.listTriggers();

        assertThat(result).extracting(ScheduledTriggerResponse::trigger)
                .containsExactly("hotel-booking.checkInReminderWeekBefore", "hotel-booking.expirePendingEvery5Minutes");
        assertThat(result.getFirst().job()).isEqualTo("hotel-booking.checkInReminderJob");
        assertThat(result.getFirst().state()).isEqualTo("NORMAL");
        assertThat(result.getFirst().triggerData()).containsEntry("daysBefore", 7).containsEntry("batchSize", 500);
        assertThat(result.getFirst().nextFireTime()).isNull(); // not scheduled in this mock
    }
}
