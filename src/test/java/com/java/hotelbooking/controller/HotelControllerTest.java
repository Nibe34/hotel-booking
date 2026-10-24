package com.java.hotelbooking.controller;

import com.java.hotelbooking.domain.dto.HotelRequest;
import com.java.hotelbooking.domain.dto.HotelResponse;
import com.java.hotelbooking.domain.dto.RoomRequest;
import com.java.hotelbooking.domain.dto.RoomResponse;
import com.java.hotelbooking.domain.enums.RoomType;
import com.java.hotelbooking.service.HotelService;
import com.java.hotelbooking.service.RoomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelControllerTest {

    @Mock
    private HotelService hotelService;

    @Mock
    private RoomService roomService;

    @InjectMocks
    private HotelController controller;

    private final HotelRequest hotelRequest = new HotelRequest("Grand", "Kyiv", "Main 1", 4, null);
    private final HotelResponse hotelResponse = new HotelResponse(1L, "Grand", "Kyiv", "Main 1", 4, null);
    private final RoomRequest roomRequest = new RoomRequest("101", RoomType.SINGLE, 1, new BigDecimal("50.00"));
    private final RoomResponse roomResponse =
            new RoomResponse(10L, 1L, "Grand", "101", RoomType.SINGLE, 1, new BigDecimal("50.00"));

    @Test
    void list_passesCityFilter() {
        List<HotelResponse> expected = List.of(hotelResponse);
        when(hotelService.list("Kyiv")).thenReturn(expected);

        assertThat(controller.list("Kyiv")).isSameAs(expected);
    }

    @Test
    void getById_delegatesWithGivenId() {
        when(hotelService.getById(1L)).thenReturn(hotelResponse);

        assertThat(controller.getById(1L)).isSameAs(hotelResponse);
    }

    @Test
    void create_delegatesRequestToService() {
        when(hotelService.create(hotelRequest)).thenReturn(hotelResponse);

        assertThat(controller.create(hotelRequest)).isSameAs(hotelResponse);
    }

    @Test
    void update_delegatesIdAndRequestToService() {
        when(hotelService.update(1L, hotelRequest)).thenReturn(hotelResponse);

        assertThat(controller.update(1L, hotelRequest)).isSameAs(hotelResponse);
    }

    @Test
    void delete_delegatesIdToService() {
        controller.delete(1L);

        verify(hotelService).delete(1L);
    }

    @Test
    void listRooms_delegatesToRoomService() {
        List<RoomResponse> expected = List.of(roomResponse);
        when(roomService.listByHotel(1L)).thenReturn(expected);

        assertThat(controller.listRooms(1L)).isSameAs(expected);
    }

    @Test
    void createRoom_delegatesHotelIdAndRequestToRoomService() {
        when(roomService.create(1L, roomRequest)).thenReturn(roomResponse);

        assertThat(controller.createRoom(1L, roomRequest)).isSameAs(roomResponse);
    }
}
