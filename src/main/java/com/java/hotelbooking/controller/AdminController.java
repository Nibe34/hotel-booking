package com.java.hotelbooking.controller;

import com.java.hotelbooking.domain.dto.AdminStatsResponse;
import com.java.hotelbooking.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No method-level annotations here on purpose: /api/admin/** is restricted to ADMIN
 * only in the SecurityFilterChain.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminService.stats();
    }
}
