package com.java.hotelbooking.job;

import com.java.hotelbooking.configuration.QuartzConfig;
import com.java.hotelbooking.service.CheckInReminderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckInReminderJobTest {

    @Mock
    private CheckInReminderService reminderService;

    @Test
    void executeInternal_passesParametersToOneServiceCall() throws Exception {
        CheckInReminderJob job = new CheckInReminderJob(reminderService);
        job.setDaysBefore(7);
        job.setBatchSize(500);

        job.executeInternal(null);

        verify(reminderService).sendReminders(7, 500);
        verifyNoMoreInteractions(reminderService);
    }

    @Test
    void executeInternal_withoutParameters_usesDocumentedDefaults() throws Exception {
        new CheckInReminderJob(reminderService).executeInternal(null);

        verify(reminderService).sendReminders(1, 100);
    }

    @Test
    void execute_weekBeforeTriggerData_overridesDaysBefore() throws Exception {
        new CheckInReminderJob(reminderService).execute(JobTestSupport.context(QuartzConfig.CHECK_IN_REMINDER_JOB, 0,
                Map.of("daysBefore", 7, "batchSize", 100, "maxRetries", 0)));

        verify(reminderService).sendReminders(7, 100);
    }

    @Test
    void job_isNotAllowedToRunConcurrently() {
        assertThat(CheckInReminderJob.class.isAnnotationPresent(DisallowConcurrentExecution.class)).isTrue();
    }

    @Test
    void failure_withDefaultZeroRetries_isLoggedAndWaitsForNextRun() throws Exception {
        when(reminderService.sendReminders(anyInt(), anyInt())).thenThrow(new IllegalStateException("mail down"));
        CheckInReminderJob job = new CheckInReminderJob(reminderService);
        var context = JobTestSupport.context(QuartzConfig.CHECK_IN_REMINDER_JOB, 0, Map.of());

        assertThatThrownBy(() -> job.executeInternal(context))
                .isInstanceOfSatisfying(JobExecutionException.class, e -> assertThat(e.refireImmediately()).isFalse());
    }
}
