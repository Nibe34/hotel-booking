package com.java.hotelbooking.controller;

import com.java.hotelbooking.domain.dto.BookingCreateRequest;
import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.service.BookingDocumentService;
import com.java.hotelbooking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real HTTP requests through Spring MVC: the {@code @CurrentUsername} value reaches the service,
 * {@code @PostCreated} gives 201 and {@code @ValidStayPeriod} turns into a 400 with a field error.
 */
@WebMvcTest(
        controllers = BookingController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
class BookingControllerWebMvcTest {

    private static final Principal BOB =
            new UsernamePasswordAuthenticationToken("bob", "n/a", AuthorityUtils.createAuthorityList("ROLE_USER"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private BookingDocumentService bookingDocumentService;

    private BookingResponse response(long id, String guest) {
        return new BookingResponse(id, 1L, "101", 1L, "Grand", guest, LocalDate.of(2099, 7, 1),
                LocalDate.of(2099, 7, 3), 2, new BigDecimal("200.00"), BookingStatus.PENDING, null);
    }

    @Test
    void create_usesUsernameOfCaller_andReturns201() throws Exception {
        when(bookingService.create(any(BookingCreateRequest.class), eq("bob"))).thenReturn(response(9L, "bob"));

        mockMvc.perform(post("/api/bookings")
                        .principal(BOB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomId":1,"checkIn":"2099-07-01","checkOut":"2099-07-03","guestsCount":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.guestUsername").value("bob"));

        verify(bookingService).create(new BookingCreateRequest(1L, LocalDate.of(2099, 7, 1),
                LocalDate.of(2099, 7, 3), 2), "bob");
    }

    @Test
    void myBookings_returnsBookingsOfCaller() throws Exception {
        when(bookingService.listMine("bob")).thenReturn(List.of(response(1L, "bob"), response(2L, "bob")));

        mockMvc.perform(get("/api/bookings/my").principal(BOB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].guestUsername").value("bob"));

        verify(bookingService).listMine("bob");
    }

    @Test
    void create_checkOutBeforeCheckIn_returns400WithExplainingFieldError() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .principal(BOB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomId":1,"checkIn":"2099-07-05","checkOut":"2099-07-01","guestsCount":2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.checkOut")
                        .value("checkOut (2099-07-01) must be after checkIn (2099-07-05)"));

        verify(bookingService, never()).create(any(), anyString());
    }

    @Test
    void create_tooLongStay_returns400WithExplainingFieldError() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .principal(BOB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomId":1,"checkIn":"2099-07-01","checkOut":"2099-09-01","guestsCount":2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.checkOut")
                        .value("stay of 62 nights (2099-07-01 - 2099-09-01) exceeds the maximum of 30 nights"));
    }
}
