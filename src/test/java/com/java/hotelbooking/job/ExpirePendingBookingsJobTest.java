package com.java.hotelbooking.job;

import com.java.hotelbooking.configuration.QuartzConfig;
import com.java.hotelbooking.service.BookingMaintenanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionException;
import org.springframework.beans.PropertyBatchUpdateException;
import org.springframework.beans.TypeMismatchException;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpirePendingBookingsJobTest {

    @Mock
    private BookingMaintenanceService maintenanceService;

    // ---------- thin job: read parameters, one service call ----------

    @Test
    void executeInternal_passesParametersToOneServiceCall() throws Exception {
        ExpirePendingBookingsJob job = new ExpirePendingBookingsJob(maintenanceService);
        job.setMaxPendingMinutes(30);   // what QuartzJobBean does with the "maxPendingMinutes" key
        job.setBatchSize(10);

        job.executeInternal(null);

        verify(maintenanceService).expirePendingBookings(Duration.ofMinutes(30), 10);
        verifyNoMoreInteractions(maintenanceService);
    }

    @Test
    void executeInternal_withoutParameters_usesDocumentedDefaults() throws Exception {
        new ExpirePendingBookingsJob(maintenanceService).executeInternal(null);

        verify(maintenanceService).expirePendingBookings(Duration.ofHours(24), 100);
    }

    @Test
    void job_isNotAllowedToRunConcurrently() {
        assertThat(ExpirePendingBookingsJob.class.isAnnotationPresent(DisallowConcurrentExecution.class)).isTrue();
    }

    // ---------- JobDataMap binding through the real QuartzJobBean.execute ----------

    @Test
    void execute_mergedDataMap_isBoundToTheJob() throws Exception {
        new ExpirePendingBookingsJob(maintenanceService).execute(JobTestSupport.context(QuartzConfig.EXPIRE_PENDING_JOB, 0,
                Map.of("maxPendingMinutes", 15L, "batchSize", 5)));

        verify(maintenanceService).expirePendingBookings(Duration.ofMinutes(15), 5);
    }

    @Test
    void execute_misspelledKey_isIgnoredByQuartz_andTheDefaultIsUsed() throws Exception {
        new ExpirePendingBookingsJob(maintenanceService).execute(JobTestSupport.context(QuartzConfig.EXPIRE_PENDING_JOB, 0,
                Map.of("maxPendingMinuts", 15L, "batchSize", 5)));

        verify(maintenanceService).expirePendingBookings(Duration.ofHours(24), 5);
    }

    @Test
    void execute_valueOfWrongType_failsBeforeCallingTheService() throws Exception {
        ExpirePendingBookingsJob job = new ExpirePendingBookingsJob(maintenanceService);
        var context = JobTestSupport.context(QuartzConfig.EXPIRE_PENDING_JOB, 0, Map.of("batchSize", "ten"));

        // Quartz logs this as an unhandled job exception; the trigger keeps its schedule
        assertThatThrownBy(() -> job.execute(context))
                .isInstanceOfSatisfying(PropertyBatchUpdateException.class, e ->
                        assertThat(e.getPropertyAccessException("batchSize")).isInstanceOf(TypeMismatchException.class));
        verify(maintenanceService, never()).expirePendingBookings(any(), anyInt());
    }

    // ---------- failure strategy: bounded retries ----------

    @Test
    void failure_beforeRetriesAreUsedUp_asksQuartzToRunAgainNow() throws Exception {
        when(maintenanceService.expirePendingBookings(any(), anyInt())).thenThrow(new IllegalStateException("DB down"));
        ExpirePendingBookingsJob job = new ExpirePendingBookingsJob(maintenanceService);
        job.setMaxRetries(2);
        var firstAttempt = JobTestSupport.context(QuartzConfig.EXPIRE_PENDING_JOB, 0, Map.of());

        assertThatThrownBy(() -> job.executeInternal(firstAttempt))
                .isInstanceOfSatisfying(JobExecutionException.class, e -> assertThat(e.refireImmediately()).isTrue())
                .hasRootCauseMessage("DB down");
    }

    @Test
    void failure_afterLastRetry_givesUpUntilNextScheduledRun() throws Exception {
        when(maintenanceService.expirePendingBookings(any(), anyInt())).thenThrow(new IllegalStateException("DB down"));
        ExpirePendingBookingsJob job = new ExpirePendingBookingsJob(maintenanceService);
        job.setMaxRetries(2);
        var thirdAttempt = JobTestSupport.context(QuartzConfig.EXPIRE_PENDING_JOB, 2, Map.of());

        assertThatThrownBy(() -> job.executeInternal(thirdAttempt))
                .isInstanceOfSatisfying(JobExecutionException.class, e -> {
                    assertThat(e.refireImmediately()).isFalse();
                    assertThat(e.unscheduleFiringTrigger()).isFalse(); // the regular schedule stays
                });
    }
}
