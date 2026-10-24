package com.java.hotelbooking.service;

import com.java.hotelbooking.domain.dto.BookingCreateRequest;
import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.enums.AuditAction;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.domain.event.BookingChangedEvent;
import com.java.hotelbooking.domain.enums.RoomType;
import com.java.hotelbooking.domain.mapper.BookingMapper;
import com.java.hotelbooking.domain.model.Booking;
import com.java.hotelbooking.domain.model.Hotel;
import com.java.hotelbooking.domain.model.Room;
import com.java.hotelbooking.exception.BadRequestException;
import com.java.hotelbooking.exception.ConflictException;
import com.java.hotelbooking.exception.NotFoundException;
import com.java.hotelbooking.repository.BookingRepository;
import com.java.hotelbooking.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final String GUEST = "bob";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookingMapper bookingMapper;

    @Mock
    private Clock clock;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BookingService bookingService;

    private Room room(long id, int capacity, String pricePerNight) {
        Hotel hotel = new Hotel();
        hotel.setId(1L);
        hotel.setName("Grand Kyiv");
        Room room = new Room();
        room.setId(id);
        room.setHotel(hotel);
        room.setRoomNumber("10" + id);
        room.setType(RoomType.DOUBLE);
        room.setCapacity(capacity);
        room.setPricePerNight(new BigDecimal(pricePerNight));
        return room;
    }

    private Booking booking(long id, String guest, BookingStatus status, LocalDate checkIn) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setRoom(room(1L, 2, "100.00"));
        booking.setGuestUsername(guest);
        booking.setCheckIn(checkIn);
        booking.setCheckOut(checkIn.plusDays(2));
        booking.setGuestsCount(1);
        booking.setTotalPrice(new BigDecimal("200.00"));
        booking.setStatus(status);
        return booking;
    }

    private BookingResponse response(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getRoom().getId(), booking.getRoom().getRoomNumber(),
                booking.getRoom().getHotel().getId(), booking.getRoom().getHotel().getName(),
                booking.getGuestUsername(), booking.getCheckIn(), booking.getCheckOut(), booking.getGuestsCount(),
                booking.getTotalPrice(), booking.getStatus(), booking.getCreatedAt());
    }

    private Authentication auth(String username, String... roles) {
        return new UsernamePasswordAuthenticationToken(username, "n/a", AuthorityUtils.createAuthorityList(roles));
    }

    // lenient: the admin path must not look at the date at all, so the stub may stay unused
    private void todayIs(LocalDate date) {
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        lenient().when(clock.instant()).thenReturn(date.atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private void mapperEchoesEntity() {
        when(bookingMapper.toResponse(any(Booking.class))).thenAnswer(inv -> response(inv.getArgument(0)));
    }

    // ---------- create ----------

    @Test
    void create_validRequest_savesPendingBookingWithPriceForAllNights() {
        Room room = room(5L, 2, "120.50");
        LocalDate checkIn = TODAY.plusDays(10);
        BookingCreateRequest request = new BookingCreateRequest(5L, checkIn, checkIn.plusDays(3), 2);
        when(roomRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsOverlapping(5L, checkIn, checkIn.plusDays(3), BookingService.ACTIVE_STATUSES))
                .thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        mapperEchoesEntity();

        BookingResponse result = bookingService.create(request, GUEST);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking saved = captor.getValue();
        assertThat(saved.getRoom()).isSameAs(room);
        assertThat(saved.getGuestUsername()).isEqualTo(GUEST);
        assertThat(saved.getCheckIn()).isEqualTo(checkIn);
        assertThat(saved.getCheckOut()).isEqualTo(checkIn.plusDays(3));
        assertThat(saved.getGuestsCount()).isEqualTo(2);
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(saved.getTotalPrice()).isEqualByComparingTo("361.50");
        assertThat(result.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(result.totalPrice()).isEqualByComparingTo("361.50");
        verify(eventPublisher).publishEvent(new BookingChangedEvent(result, AuditAction.CREATE));
    }

    @ParameterizedTest
    @CsvSource({
            "1,  80.00",
            "7,  560.00",
            "30, 2400.00"
    })
    void create_nightsWithinLimit_totalIsNightsTimesPrice(int nights, String expectedTotal) {
        LocalDate checkIn = TODAY.plusDays(1);
        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room(1L, 2, "80.00")));
        when(bookingRepository.existsOverlapping(eq(1L), any(), any(), any())).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        mapperEchoesEntity();

        bookingService.create(new BookingCreateRequest(1L, checkIn, checkIn.plusDays(nights), 1), GUEST);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalPrice()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    void create_guestsEqualToCapacity_isAllowed() {
        LocalDate checkIn = TODAY.plusDays(1);
        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room(1L, 3, "50.00")));
        when(bookingRepository.existsOverlapping(eq(1L), any(), any(), any())).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        mapperEchoesEntity();

        BookingResponse result = bookingService.create(
                new BookingCreateRequest(1L, checkIn, checkIn.plusDays(1), 3), GUEST);

        assertThat(result.guestsCount()).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({
            "0",
            "-2"
    })
    void create_checkOutNotAfterCheckIn_throwsBadRequest(int nights) {
        LocalDate checkIn = TODAY.plusDays(5);
        BookingCreateRequest request = new BookingCreateRequest(1L, checkIn, checkIn.plusDays(nights), 1);

        assertThatThrownBy(() -> bookingService.create(request, GUEST))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("checkOut must be after checkIn");
        verify(roomRepository, never()).findByIdForUpdate(anyLong());
        verify(bookingRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void create_moreThanThirtyNights_throwsBadRequest() {
        LocalDate checkIn = TODAY.plusDays(1);
        BookingCreateRequest request = new BookingCreateRequest(1L, checkIn, checkIn.plusDays(31), 1);

        assertThatThrownBy(() -> bookingService.create(request, GUEST))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A booking cannot be longer than 30 nights");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void create_unknownRoom_throwsNotFound() {
        LocalDate checkIn = TODAY.plusDays(1);
        when(roomRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.create(
                new BookingCreateRequest(99L, checkIn, checkIn.plusDays(2), 1), GUEST))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Room with id 99 not found");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void create_moreGuestsThanCapacity_throwsBadRequest() {
        LocalDate checkIn = TODAY.plusDays(1);
        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room(1L, 2, "50.00")));

        assertThatThrownBy(() -> bookingService.create(
                new BookingCreateRequest(1L, checkIn, checkIn.plusDays(2), 3), GUEST))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Room 101 fits at most 2 guest(s)");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void create_overlappingActiveBooking_throwsConflict() {
        LocalDate checkIn = TODAY.plusDays(1);
        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room(1L, 2, "50.00")));
        when(bookingRepository.existsOverlapping(1L, checkIn, checkIn.plusDays(2), BookingService.ACTIVE_STATUSES))
                .thenReturn(true);

        assertThatThrownBy(() -> bookingService.create(
                new BookingCreateRequest(1L, checkIn, checkIn.plusDays(2), 1), GUEST))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Room 101 is already booked for these dates");
        verify(bookingRepository, never()).save(any());
    }

    // ---------- listMine / listAll ----------

    @Test
    void listMine_returnsMappedBookingsOfGivenGuestInRepositoryOrder() {
        Booking later = booking(2L, GUEST, BookingStatus.PENDING, TODAY.plusDays(20));
        Booking earlier = booking(1L, GUEST, BookingStatus.CONFIRMED, TODAY.plusDays(5));
        when(bookingRepository.findByGuestUsernameOrderByCheckInDesc(GUEST)).thenReturn(List.of(later, earlier));
        mapperEchoesEntity();

        List<BookingResponse> result = bookingService.listMine(GUEST);

        assertThat(result).extracting(BookingResponse::id).containsExactly(2L, 1L);
        assertThat(result).allSatisfy(r -> assertThat(r.guestUsername()).isEqualTo(GUEST));
    }

    @Test
    void listMine_guestWithoutBookings_returnsEmptyList() {
        when(bookingRepository.findByGuestUsernameOrderByCheckInDesc("nobody")).thenReturn(List.of());

        assertThat(bookingService.listMine("nobody")).isEmpty();
    }

    @Test
    void listAll_returnsAllBookingsMapped() {
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                booking(3L, "alice", BookingStatus.CANCELLED, TODAY.plusDays(1)),
                booking(4L, GUEST, BookingStatus.PENDING, TODAY.plusDays(2))));
        mapperEchoesEntity();

        List<BookingResponse> result = bookingService.listAll();

        assertThat(result).extracting(BookingResponse::id).containsExactly(3L, 4L);
        assertThat(result).extracting(BookingResponse::guestUsername).containsExactly("alice", GUEST);
    }

    @Test
    void listAll_noBookings_returnsEmptyList() {
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        assertThat(bookingService.listAll()).isEmpty();
    }

    // ---------- getById ----------

    @Test
    void getById_existingBooking_returnsMappedResponse() {
        Booking booking = booking(7L, GUEST, BookingStatus.CONFIRMED, TODAY.plusDays(3));
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        mapperEchoesEntity();

        BookingResponse result = bookingService.getById(7L);

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void getById_missingBooking_throwsNotFound() {
        when(bookingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getById(404L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Booking with id 404 not found");
    }

    // ---------- cancel ----------

    @Test
    void cancel_ownerBeforeCheckIn_setsStatusCancelled() {
        todayIs(TODAY);
        Booking booking = booking(1L, GUEST, BookingStatus.PENDING, TODAY.plusDays(1));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        mapperEchoesEntity();

        BookingResponse result = bookingService.cancel(1L, auth(GUEST, "ROLE_USER"));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingMapper).toResponse(booking);
        verify(eventPublisher).publishEvent(new BookingChangedEvent(result, AuditAction.CANCEL));
    }

    @Test
    void cancel_ownerOnCheckInDay_throwsConflictAndKeepsStatus() {
        todayIs(TODAY);
        Booking booking = booking(1L, GUEST, BookingStatus.CONFIRMED, TODAY);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(1L, auth(GUEST, "ROLE_USER")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Booking can be cancelled only before the check-in date");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void cancel_ownerAfterCheckIn_throwsConflict() {
        todayIs(TODAY);
        Booking booking = booking(1L, GUEST, BookingStatus.CONFIRMED, TODAY.minusDays(1));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(1L, auth(GUEST, "ROLE_USER")))
                .isInstanceOf(ConflictException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void cancel_adminOnCheckInDay_isAllowed() {
        todayIs(TODAY);
        Booking booking = booking(1L, GUEST, BookingStatus.CONFIRMED, TODAY);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        mapperEchoesEntity();

        BookingResponse result = bookingService.cancel(1L, auth("alice", "ROLE_ADMIN", "ROLE_USER"));

        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancel_otherUsersBooking_throwsAccessDenied() {
        Booking booking = booking(1L, GUEST, BookingStatus.PENDING, TODAY.plusDays(5));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(1L, auth("mallory", "ROLE_USER")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You can cancel only your own bookings");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void cancel_alreadyCancelled_throwsConflict() {
        Booking booking = booking(1L, GUEST, BookingStatus.CANCELLED, TODAY.plusDays(5));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(1L, auth(GUEST, "ROLE_USER")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Booking 1 is already cancelled");
    }

    @Test
    void cancel_missingBooking_throwsNotFound() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancel(5L, auth(GUEST, "ROLE_USER")))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- confirm ----------

    @Test
    void confirm_pendingBooking_setsStatusConfirmed() {
        Booking booking = booking(1L, GUEST, BookingStatus.PENDING, TODAY.plusDays(5));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        mapperEchoesEntity();

        BookingResponse result = bookingService.confirm(1L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.status()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingMapper).toResponse(booking);
        verify(eventPublisher).publishEvent(new BookingChangedEvent(result, AuditAction.CONFIRM));
    }

    @ParameterizedTest
    @CsvSource({
            "CONFIRMED",
            "CANCELLED"
    })
    void confirm_notPendingBooking_throwsConflictAndKeepsStatus(BookingStatus status) {
        Booking booking = booking(1L, GUEST, status, TODAY.plusDays(5));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.confirm(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only PENDING bookings can be confirmed, current status: " + status);
        assertThat(booking.getStatus()).isEqualTo(status);
    }

    @Test
    void confirm_missingBooking_throwsNotFound() {
        when(bookingRepository.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.confirm(8L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Booking with id 8 not found");
    }

    // ---------- confirmBatch ----------

    @Test
    void confirmBatch_confirmsEveryBookingInOrder() {
        Booking first = booking(1L, GUEST, BookingStatus.PENDING, TODAY.plusDays(5));
        Booking second = booking(2L, "alice", BookingStatus.PENDING, TODAY.plusDays(6));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(first));
        when(bookingRepository.findById(2L)).thenReturn(Optional.of(second));
        mapperEchoesEntity();

        List<BookingResponse> result = bookingService.confirmBatch(List.of(1L, 2L));

        assertThat(result).extracting(BookingResponse::id).containsExactly(1L, 2L);
        assertThat(first.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(second.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void confirmBatch_notPendingBookingInBatch_throwsConflict() {
        Booking pending = booking(1L, GUEST, BookingStatus.PENDING, TODAY.plusDays(5));
        Booking cancelled = booking(2L, GUEST, BookingStatus.CANCELLED, TODAY.plusDays(5));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(bookingRepository.findById(2L)).thenReturn(Optional.of(cancelled));
        mapperEchoesEntity();

        assertThatThrownBy(() -> bookingService.confirmBatch(List.of(1L, 2L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void confirmBatch_emptyList_returnsEmptyList() {
        assertThat(bookingService.confirmBatch(List.of())).isEmpty();
    }

    // ---------- delete ----------

    @Test
    void delete_existingBooking_deletesThatEntity() {
        Booking booking = booking(1L, GUEST, BookingStatus.PENDING, TODAY.plusDays(5));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        bookingService.delete(1L);

        verify(bookingRepository).delete(booking);
    }

    @Test
    void delete_missingBooking_throwsNotFoundAndDeletesNothing() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.delete(1L))
                .isInstanceOf(NotFoundException.class);
        verify(bookingRepository, never()).delete(any());
    }

    @Test
    void activeStatuses_containOnlyPendingAndConfirmed() {
        assertThat(BookingService.ACTIVE_STATUSES)
                .containsExactlyInAnyOrder(BookingStatus.PENDING, BookingStatus.CONFIRMED);
    }
}
