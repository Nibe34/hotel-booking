package com.java.hotelbooking.controller;

import com.java.hotelbooking.configuration.QuartzConfig;
import com.java.hotelbooking.domain.dto.AdminStatsResponse;
import com.java.hotelbooking.domain.dto.BookingAuditResponse;
import com.java.hotelbooking.domain.dto.ScheduledTriggerResponse;
import com.java.hotelbooking.service.AdminService;
import com.java.hotelbooking.service.ScheduledJobsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * No method-level annotations here on purpose: /api/admin/** is restricted to ADMIN
 * only in the SecurityFilterChain.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ScheduledJobsService scheduledJobsService;

    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminService.stats();
    }

    @GetMapping("/audit")
    public List<BookingAuditResponse> audit(@RequestParam(required = false) Long bookingId) {
        return adminService.audit(bookingId);
    }

    @GetMapping("/jobs")
    public List<ScheduledTriggerResponse> jobs() {
        return scheduledJobsService.listTriggers();
    }

    @PostMapping("/jobs/expire-pending")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void runExpirePending(@RequestParam(required = false) Long maxPendingMinutes) {
        Map<String, Object> overrides = new HashMap<>();
        if (maxPendingMinutes != null) {
            overrides.put("maxPendingMinutes", maxPendingMinutes);
        }
        scheduledJobsService.runNow(QuartzConfig.EXPIRE_PENDING_JOB, overrides);
    }

    @PostMapping("/jobs/check-in-reminders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void runCheckInReminders(@RequestParam(required = false) Integer daysBefore) {
        Map<String, Object> overrides = new HashMap<>();
        if (daysBefore != null) {
            overrides.put("daysBefore", daysBefore);
        }
        scheduledJobsService.runNow(QuartzConfig.CHECK_IN_REMINDER_JOB, overrides);
    }
}
