package com.java.hotelbooking.job;

import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;

import java.util.Map;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

final class JobTestSupport {

    private JobTestSupport() {
    }

    /** A context the way Quartz builds it: refire count and the merged JobDataMap (job defaults + trigger values). */
    static JobExecutionContext context(JobKey key, int refireCount, Map<String, ?> mergedData) throws SchedulerException {
        JobExecutionContext context = mock(JobExecutionContext.class);
        JobDetail detail = mock(JobDetail.class);
        Scheduler scheduler = mock(Scheduler.class);
        lenient().when(context.getJobDetail()).thenReturn(detail);
        lenient().when(detail.getKey()).thenReturn(key);
        lenient().when(context.getRefireCount()).thenReturn(refireCount);
        lenient().when(context.getScheduler()).thenReturn(scheduler);
        lenient().when(scheduler.getContext()).thenReturn(new SchedulerContext());
        lenient().when(context.getMergedJobDataMap()).thenReturn(new JobDataMap(mergedData));
        return context;
    }
}
