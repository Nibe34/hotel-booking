package com.java.hotelbooking.controller;

import com.java.hotelbooking.domain.dto.AdminStatsResponse;
import com.java.hotelbooking.service.AdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminController controller;

    @Test
    void stats_returnsWhatServiceReturns() {
        AdminStatsResponse expected = new AdminStatsResponse(1L, 2L, Map.of());
        when(adminService.stats()).thenReturn(expected);

        assertThat(controller.stats()).isSameAs(expected);
    }
}
