package com.java.hotelbooking.controller;

import com.java.hotelbooking.domain.dto.BookingCreateRequest;
import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingController controller;

    private final Authentication bob =
            new UsernamePasswordAuthenticationToken("bob", "n/a", AuthorityUtils.createAuthorityList("ROLE_USER"));

    private BookingResponse response(long id) {
        return new BookingResponse(id, 1L, "101", 1L, "Grand", "bob", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 2), 1, new BigDecimal("100.00"), BookingStatus.PENDING, null);
    }

    @Test
    void create_passesUsernameToService() {
        BookingCreateRequest request = new BookingCreateRequest(1L, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 2), 1);
        BookingResponse expected = response(1L);
        when(bookingService.create(request, "bob")).thenReturn(expected);

        assertThat(controller.create(request, "bob")).isSameAs(expected);
    }

    @Test
    void myBookings_passesUsernameToService() {
        List<BookingResponse> expected = List.of(response(1L));
        when(bookingService.listMine("bob")).thenReturn(expected);

        assertThat(controller.myBookings("bob")).isSameAs(expected);
    }

    @Test
    void listAll_returnsWhatServiceReturns() {
        List<BookingResponse> expected = List.of(response(1L), response(2L));
        when(bookingService.listAll()).thenReturn(expected);

        assertThat(controller.listAll()).isSameAs(expected);
    }

    @Test
    void getById_delegatesWithGivenId() {
        BookingResponse expected = response(7L);
        when(bookingService.getById(7L)).thenReturn(expected);

        assertThat(controller.getById(7L)).isSameAs(expected);
    }

    @Test
    void cancel_passesIdAndAuthentication() {
        BookingResponse expected = response(3L);
        when(bookingService.cancel(3L, bob)).thenReturn(expected);

        assertThat(controller.cancel(3L, bob)).isSameAs(expected);
    }

    @Test
    void confirm_delegatesWithGivenId() {
        BookingResponse expected = response(4L);
        when(bookingService.confirm(4L)).thenReturn(expected);

        assertThat(controller.confirm(4L)).isSameAs(expected);
    }

    @Test
    void confirmBatch_delegatesIdsToService() {
        List<BookingResponse> expected = List.of(response(1L), response(2L));
        when(bookingService.confirmBatch(List.of(1L, 2L))).thenReturn(expected);

        assertThat(controller.confirmBatch(List.of(1L, 2L))).isSameAs(expected);
    }

    @Test
    void delete_delegatesIdToService() {
        controller.delete(5L);

        verify(bookingService).delete(5L);
    }
}
