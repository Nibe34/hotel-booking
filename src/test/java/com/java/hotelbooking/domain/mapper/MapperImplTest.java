package com.java.hotelbooking.domain.mapper;

import com.java.hotelbooking.domain.dto.BookingAuditResponse;
import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.dto.HotelRequest;
import com.java.hotelbooking.domain.dto.HotelResponse;
import com.java.hotelbooking.domain.dto.RoomRequest;
import com.java.hotelbooking.domain.dto.RoomResponse;
import com.java.hotelbooking.domain.enums.AuditAction;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.domain.enums.RoomType;
import com.java.hotelbooking.domain.model.Booking;
import com.java.hotelbooking.domain.model.BookingAuditEntry;
import com.java.hotelbooking.domain.model.Hotel;
import com.java.hotelbooking.domain.model.Room;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MapStruct implementations have no dependencies, so they are created directly without a Spring context.
 */
class MapperImplTest {

    private final HotelMapper hotelMapper = new HotelMapperImpl();
    private final RoomMapper roomMapper = new RoomMapperImpl();
    private final BookingMapper bookingMapper = new BookingMapperImpl();
    private final BookingAuditMapper auditMapper = new BookingAuditMapperImpl();

    private Hotel hotel() {
        Hotel hotel = new Hotel();
        hotel.setId(1L);
        hotel.setName("Grand Kyiv");
        hotel.setCity("Kyiv");
        hotel.setAddress("Khreshchatyk 1");
        hotel.setStars(5);
        hotel.setDescription("Center");
        return hotel;
    }

    private Room room() {
        Room room = new Room();
        room.setId(10L);
        room.setHotel(hotel());
        room.setRoomNumber("101");
        room.setType(RoomType.TWIN);
        room.setCapacity(2);
        room.setPricePerNight(new BigDecimal("99.90"));
        return room;
    }

    // ---------- HotelMapper ----------

    @Test
    void hotelToResponse_copiesAllFields() {
        HotelResponse response = hotelMapper.toResponse(hotel());

        assertThat(response).isEqualTo(new HotelResponse(1L, "Grand Kyiv", "Kyiv", "Khreshchatyk 1", 5, "Center"));
    }

    @Test
    void hotelToEntity_ignoresIdAndCopiesFields() {
        Hotel hotel = hotelMapper.toEntity(new HotelRequest("Opera", "Odesa", "Derybasivska 2", 3, null));

        assertThat(hotel.getId()).isNull();
        assertThat(hotel.getName()).isEqualTo("Opera");
        assertThat(hotel.getCity()).isEqualTo("Odesa");
        assertThat(hotel.getStars()).isEqualTo(3);
    }

    @Test
    void hotelUpdateEntity_overwritesFieldsButKeepsId() {
        Hotel hotel = hotel();

        hotelMapper.updateEntity(new HotelRequest("Renamed", "Lviv", "Rynok 1", 4, "New"), hotel);

        assertThat(hotel.getId()).isEqualTo(1L);
        assertThat(hotel.getName()).isEqualTo("Renamed");
        assertThat(hotel.getCity()).isEqualTo("Lviv");
        assertThat(hotel.getDescription()).isEqualTo("New");
    }

    @Test
    void hotelMapper_nullInput_returnsNull() {
        assertThat(hotelMapper.toResponse(null)).isNull();
        assertThat(hotelMapper.toEntity(null)).isNull();
    }

    // ---------- RoomMapper ----------

    @Test
    void roomToResponse_flattensHotel() {
        RoomResponse response = roomMapper.toResponse(room());

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.hotelId()).isEqualTo(1L);
        assertThat(response.hotelName()).isEqualTo("Grand Kyiv");
        assertThat(response.type()).isEqualTo(RoomType.TWIN);
        assertThat(response.pricePerNight()).isEqualByComparingTo("99.90");
    }

    @Test
    void roomToResponse_withoutHotel_leavesHotelFieldsNull() {
        Room room = room();
        room.setHotel(null);

        RoomResponse response = roomMapper.toResponse(room);

        assertThat(response.hotelId()).isNull();
        assertThat(response.hotelName()).isNull();
    }

    @Test
    void roomToEntity_ignoresIdAndHotel() {
        Room room = roomMapper.toEntity(new RoomRequest("202", RoomType.SUITE, 4, new BigDecimal("300.00")));

        assertThat(room.getId()).isNull();
        assertThat(room.getHotel()).isNull();
        assertThat(room.getRoomNumber()).isEqualTo("202");
        assertThat(room.getCapacity()).isEqualTo(4);
    }

    @Test
    void roomUpdateEntity_keepsIdAndHotel() {
        Room room = room();
        Hotel hotel = room.getHotel();

        roomMapper.updateEntity(new RoomRequest("999", RoomType.FAMILY, 6, new BigDecimal("500.00")), room);

        assertThat(room.getId()).isEqualTo(10L);
        assertThat(room.getHotel()).isSameAs(hotel);
        assertThat(room.getRoomNumber()).isEqualTo("999");
        assertThat(room.getType()).isEqualTo(RoomType.FAMILY);
    }

    @Test
    void roomMapper_nullInput_returnsNull() {
        assertThat(roomMapper.toResponse(null)).isNull();
        assertThat(roomMapper.toEntity(null)).isNull();
    }

    // ---------- BookingMapper ----------

    @Test
    void bookingToResponse_flattensRoomAndHotel() {
        Booking booking = new Booking();
        booking.setId(100L);
        booking.setRoom(room());
        booking.setGuestUsername("bob");
        booking.setCheckIn(LocalDate.of(2026, 11, 1));
        booking.setCheckOut(LocalDate.of(2026, 11, 3));
        booking.setGuestsCount(2);
        booking.setTotalPrice(new BigDecimal("199.80"));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCreatedAt(Instant.parse("2026-10-20T10:00:00Z"));

        BookingResponse response = bookingMapper.toResponse(booking);

        assertThat(response).isEqualTo(new BookingResponse(100L, 10L, "101", 1L, "Grand Kyiv", "bob",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), 2, new BigDecimal("199.80"),
                BookingStatus.CONFIRMED, Instant.parse("2026-10-20T10:00:00Z")));
    }

    @Test
    void bookingToResponse_withoutRoom_leavesRoomFieldsNull() {
        Booking booking = new Booking();
        booking.setId(1L);

        BookingResponse response = bookingMapper.toResponse(booking);

        assertThat(response.roomId()).isNull();
        assertThat(response.hotelId()).isNull();
        assertThat(response.hotelName()).isNull();
    }

    @Test
    void bookingMapper_nullInput_returnsNull() {
        assertThat(bookingMapper.toResponse(null)).isNull();
    }

    // ---------- BookingAuditMapper ----------

    @Test
    void auditToResponse_copiesAllFields() {
        BookingAuditEntry entry = new BookingAuditEntry();
        entry.setId(1L);
        entry.setBookingId(5L);
        entry.setAction(AuditAction.CONFIRM);
        entry.setStatus(BookingStatus.CONFIRMED);
        entry.setActor("alice");
        entry.setCreatedAt(Instant.parse("2026-10-24T12:00:00Z"));

        assertThat(auditMapper.toResponse(entry)).isEqualTo(new BookingAuditResponse(1L, 5L, AuditAction.CONFIRM,
                BookingStatus.CONFIRMED, "alice", Instant.parse("2026-10-24T12:00:00Z")));
        assertThat(auditMapper.toResponse(null)).isNull();
    }
}
