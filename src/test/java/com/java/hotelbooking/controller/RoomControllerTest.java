package com.java.hotelbooking.controller;

import com.java.hotelbooking.domain.dto.RoomRequest;
import com.java.hotelbooking.domain.dto.RoomResponse;
import com.java.hotelbooking.domain.enums.RoomType;
import com.java.hotelbooking.service.RoomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    @Mock
    private RoomService roomService;

    @InjectMocks
    private RoomController controller;

    private final RoomRequest request = new RoomRequest("101", RoomType.DOUBLE, 2, new BigDecimal("80.00"));
    private final RoomResponse response =
            new RoomResponse(1L, 1L, "Grand", "101", RoomType.DOUBLE, 2, new BigDecimal("80.00"));

    @Test
    void available_passesDatesAndGuests() {
        LocalDate checkIn = LocalDate.of(2026, 11, 1);
        List<RoomResponse> expected = List.of(response);
        when(roomService.findAvailable(checkIn, checkIn.plusDays(2), 2)).thenReturn(expected);

        assertThat(controller.available(checkIn, checkIn.plusDays(2), 2)).isSameAs(expected);
    }

    @Test
    void getById_delegatesWithGivenId() {
        when(roomService.getById(1L)).thenReturn(response);

        assertThat(controller.getById(1L)).isSameAs(response);
    }

    @Test
    void update_delegatesIdAndRequestToService() {
        when(roomService.update(1L, request)).thenReturn(response);

        assertThat(controller.update(1L, request)).isSameAs(response);
    }

    @Test
    void delete_delegatesIdToService() {
        controller.delete(1L);

        verify(roomService).delete(1L);
    }
}
