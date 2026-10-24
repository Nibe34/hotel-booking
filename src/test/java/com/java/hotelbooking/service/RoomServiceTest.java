package com.java.hotelbooking.service;

import com.java.hotelbooking.domain.dto.RoomRequest;
import com.java.hotelbooking.domain.dto.RoomResponse;
import com.java.hotelbooking.domain.enums.RoomType;
import com.java.hotelbooking.domain.mapper.RoomMapper;
import com.java.hotelbooking.domain.model.Hotel;
import com.java.hotelbooking.domain.model.Room;
import com.java.hotelbooking.exception.BadRequestException;
import com.java.hotelbooking.exception.ConflictException;
import com.java.hotelbooking.exception.NotFoundException;
import com.java.hotelbooking.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2026, 11, 1);

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private HotelService hotelService;

    @Mock
    private RoomMapper roomMapper;

    @InjectMocks
    private RoomService roomService;

    private Hotel hotel(long id) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setName("Hotel " + id);
        return hotel;
    }

    private Room room(long id, Hotel hotel, String number) {
        Room room = new Room();
        room.setId(id);
        room.setHotel(hotel);
        room.setRoomNumber(number);
        room.setType(RoomType.DOUBLE);
        room.setCapacity(2);
        room.setPricePerNight(new BigDecimal("100.00"));
        return room;
    }

    private RoomResponse response(Room room) {
        return new RoomResponse(room.getId(), room.getHotel() == null ? null : room.getHotel().getId(),
                room.getHotel() == null ? null : room.getHotel().getName(), room.getRoomNumber(), room.getType(),
                room.getCapacity(), room.getPricePerNight());
    }

    private RoomRequest request(String number) {
        return new RoomRequest(number, RoomType.SUITE, 4, new BigDecimal("250.00"));
    }

    private void mapperEchoesEntity() {
        when(roomMapper.toResponse(any(Room.class))).thenAnswer(inv -> response(inv.getArgument(0)));
    }

    // ---------- listByHotel ----------

    @Test
    void listByHotel_existingHotel_returnsItsRoomsMapped() {
        Hotel hotel = hotel(1L);
        when(roomRepository.findByHotelIdOrderByRoomNumber(1L))
                .thenReturn(List.of(room(1L, hotel, "101"), room(2L, hotel, "102")));
        mapperEchoesEntity();

        List<RoomResponse> result = roomService.listByHotel(1L);

        verify(hotelService).findHotel(1L);
        assertThat(result).extracting(RoomResponse::roomNumber).containsExactly("101", "102");
    }

    @Test
    void listByHotel_missingHotel_throwsNotFoundWithoutQueryingRooms() {
        when(hotelService.findHotel(5L)).thenThrow(new NotFoundException("Hotel", 5L));

        assertThatThrownBy(() -> roomService.listByHotel(5L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Hotel with id 5 not found");
        verify(roomRepository, never()).findByHotelIdOrderByRoomNumber(anyLong());
    }

    // ---------- getById ----------

    @Test
    void getById_existingRoom_returnsMappedResponse() {
        Room room = room(3L, hotel(1L), "301");
        when(roomRepository.findById(3L)).thenReturn(Optional.of(room));
        mapperEchoesEntity();

        RoomResponse result = roomService.getById(3L);

        assertThat(result.id()).isEqualTo(3L);
        assertThat(result.roomNumber()).isEqualTo("301");
    }

    @Test
    void getById_missingRoom_throwsNotFound() {
        when(roomRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getById(3L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Room with id 3 not found");
    }

    // ---------- findAvailable ----------

    @Test
    void findAvailable_validRange_queriesWithActiveStatuses() {
        Room free = room(4L, hotel(1L), "401");
        when(roomRepository.findAvailable(CHECK_IN, CHECK_IN.plusDays(2), 2, BookingService.ACTIVE_STATUSES))
                .thenReturn(List.of(free));
        mapperEchoesEntity();

        List<RoomResponse> result = roomService.findAvailable(CHECK_IN, CHECK_IN.plusDays(2), 2);

        assertThat(result).extracting(RoomResponse::id).containsExactly(4L);
        verify(roomRepository).findAvailable(CHECK_IN, CHECK_IN.plusDays(2), 2, BookingService.ACTIVE_STATUSES);
    }

    @Test
    void findAvailable_singleNight_isAccepted() {
        when(roomRepository.findAvailable(CHECK_IN, CHECK_IN.plusDays(1), 1, BookingService.ACTIVE_STATUSES))
                .thenReturn(List.of());

        assertThat(roomService.findAvailable(CHECK_IN, CHECK_IN.plusDays(1), 1)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void findAvailable_checkOutNotAfterCheckIn_throwsBadRequest(int nights) {
        assertThatThrownBy(() -> roomService.findAvailable(CHECK_IN, CHECK_IN.plusDays(nights), 1))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("checkOut must be after checkIn");
        verify(roomRepository, never()).findAvailable(any(), any(), anyInt(), any());
    }

    // ---------- create ----------

    @Test
    void create_newRoomNumber_attachesHotelAndSaves() {
        Hotel hotel = hotel(1L);
        RoomRequest request = request("501");
        Room mapped = room(0L, null, "501");
        mapped.setId(null);
        when(hotelService.findHotel(1L)).thenReturn(hotel);
        when(roomRepository.existsByHotelIdAndRoomNumber(1L, "501")).thenReturn(false);
        when(roomMapper.toEntity(request)).thenReturn(mapped);
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> {
            Room r = inv.getArgument(0);
            r.setId(50L);
            return r;
        });
        mapperEchoesEntity();

        RoomResponse result = roomService.create(1L, request);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        assertThat(captor.getValue().getHotel()).isSameAs(hotel);
        assertThat(result.id()).isEqualTo(50L);
        assertThat(result.hotelId()).isEqualTo(1L);
    }

    @Test
    void create_duplicateRoomNumber_throwsConflictAndDoesNotSave() {
        when(hotelService.findHotel(1L)).thenReturn(hotel(1L));
        when(roomRepository.existsByHotelIdAndRoomNumber(1L, "101")).thenReturn(true);

        assertThatThrownBy(() -> roomService.create(1L, request("101")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Room 101 already exists in this hotel");
        verify(roomRepository, never()).save(any());
    }

    @Test
    void create_missingHotel_throwsNotFound() {
        when(hotelService.findHotel(7L)).thenThrow(new NotFoundException("Hotel", 7L));

        assertThatThrownBy(() -> roomService.create(7L, request("101")))
                .isInstanceOf(NotFoundException.class);
        verify(roomRepository, never()).save(any());
    }

    // ---------- update ----------

    @Test
    void update_sameRoomNumber_skipsDuplicateCheckAndUpdates() {
        Room room = room(1L, hotel(1L), "101");
        RoomRequest request = request("101");
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        mapperEchoesEntity();

        roomService.update(1L, request);

        verify(roomRepository, never()).existsByHotelIdAndRoomNumber(anyLong(), anyString());
        verify(roomMapper).updateEntity(request, room);
    }

    @Test
    void update_newFreeRoomNumber_checksWithinSameHotelAndUpdates() {
        Room room = room(1L, hotel(2L), "101");
        RoomRequest request = request("102");
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(roomRepository.existsByHotelIdAndRoomNumber(2L, "102")).thenReturn(false);
        mapperEchoesEntity();

        RoomResponse result = roomService.update(1L, request);

        verify(roomRepository).existsByHotelIdAndRoomNumber(2L, "102");
        verify(roomMapper).updateEntity(request, room);
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void update_takenRoomNumber_throwsConflictAndDoesNotModify() {
        Room room = room(1L, hotel(2L), "101");
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(roomRepository.existsByHotelIdAndRoomNumber(2L, "102")).thenReturn(true);

        assertThatThrownBy(() -> roomService.update(1L, request("102")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Room 102 already exists in this hotel");
        verify(roomMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_missingRoom_throwsNotFound() {
        when(roomRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.update(1L, request("101")))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- delete ----------

    @Test
    void delete_existingRoom_deletesThatEntity() {
        Room room = room(1L, hotel(1L), "101");
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        roomService.delete(1L);

        verify(roomRepository).delete(room);
    }

    @Test
    void delete_missingRoom_throwsNotFoundAndDeletesNothing() {
        when(roomRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.delete(1L))
                .isInstanceOf(NotFoundException.class);
        verify(roomRepository, never()).delete(any());
    }
}
