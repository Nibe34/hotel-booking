package com.java.hotelbooking.service;

import com.java.hotelbooking.configuration.QuartzConfig;
import com.java.hotelbooking.domain.dto.ScheduledTriggerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Admin view of the Quartz schedule and manual runs of the jobs. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobsService {

    private final Scheduler scheduler;

    public List<ScheduledTriggerResponse> listTriggers() {
        try {
            return scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(QuartzConfig.GROUP)).stream()
                    .sorted(Comparator.comparing(TriggerKey::getName))
                    .map(this::describe)
                    .toList();
        } catch (SchedulerException e) {
            throw new IllegalStateException("Cannot read the Quartz schedule", e);
        }
    }

    /** Runs the job once now; {@code overrides} act like trigger data and win over the job's defaults. */
    public void runNow(JobKey job, Map<String, Object> overrides) {
        try {
            scheduler.triggerJob(job, new JobDataMap(overrides));
            log.info("{} triggered manually with {}", job, overrides);
        } catch (SchedulerException e) {
            throw new IllegalStateException("Cannot trigger " + job, e);
        }
    }

    private ScheduledTriggerResponse describe(TriggerKey key) {
        try {
            Trigger trigger = scheduler.getTrigger(key);
            return new ScheduledTriggerResponse(trigger.getJobKey().toString(), key.toString(),
                    trigger.getDescription(), scheduler.getTriggerState(key).name(),
                    toInstant(trigger.getNextFireTime()), toInstant(trigger.getPreviousFireTime()),
                    new TreeMap<>(trigger.getJobDataMap().getWrappedMap()));
        } catch (SchedulerException e) {
            throw new IllegalStateException("Cannot read trigger " + key, e);
        }
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
