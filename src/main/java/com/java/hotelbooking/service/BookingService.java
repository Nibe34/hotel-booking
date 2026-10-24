package com.java.hotelbooking.service;

import com.java.hotelbooking.domain.dto.BookingCreateRequest;
import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.domain.mapper.BookingMapper;
import com.java.hotelbooking.domain.model.Booking;
import com.java.hotelbooking.domain.model.Room;
import com.java.hotelbooking.exception.BadRequestException;
import com.java.hotelbooking.exception.ConflictException;
import com.java.hotelbooking.exception.NotFoundException;
import com.java.hotelbooking.repository.BookingRepository;
import com.java.hotelbooking.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Access to bookings is controlled with method security only: the SecurityFilterChain
 * just requires an authenticated user for /api/bookings/**, the role checks live here.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService {

    static final Set<BookingStatus> ACTIVE_STATUSES = Set.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);
    private static final int MAX_NIGHTS = 30;

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final BookingMapper bookingMapper;

    @Transactional
    @PreAuthorize("hasRole('USER')")
    public BookingResponse create(BookingCreateRequest request, String username) {
        long nights = ChronoUnit.DAYS.between(request.checkIn(), request.checkOut());
        if (nights <= 0) {
            throw new BadRequestException("checkOut must be after checkIn");
        }
        if (nights > MAX_NIGHTS) {
            throw new BadRequestException("A booking cannot be longer than " + MAX_NIGHTS + " nights");
        }

        Room room = roomRepository.findByIdForUpdate(request.roomId())
                .orElseThrow(() -> new NotFoundException("Room", request.roomId()));
        if (request.guestsCount() > room.getCapacity()) {
            throw new BadRequestException("Room " + room.getRoomNumber() + " fits at most "
                    + room.getCapacity() + " guest(s)");
        }
        if (bookingRepository.existsOverlapping(room.getId(), request.checkIn(), request.checkOut(),
                ACTIVE_STATUSES)) {
            throw new ConflictException("Room " + room.getRoomNumber() + " is already booked for these dates");
        }

        Booking booking = new Booking();
        booking.setRoom(room);
        booking.setGuestUsername(username);
        booking.setCheckIn(request.checkIn());
        booking.setCheckOut(request.checkOut());
        booking.setGuestsCount(request.guestsCount());
        booking.setTotalPrice(room.getPricePerNight().multiply(BigDecimal.valueOf(nights)));
        booking.setStatus(BookingStatus.PENDING);
        return bookingMapper.toResponse(bookingRepository.save(booking));
    }

    @PreAuthorize("hasRole('USER')")
    public List<BookingResponse> listMine(String username) {
        return bookingRepository.findByGuestUsernameOrderByCheckInDesc(username).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<BookingResponse> listAll() {
        return bookingRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    @PostAuthorize("hasRole('ADMIN') or returnObject.guestUsername() == authentication.name")
    public BookingResponse getById(Long id) {
        return bookingMapper.toResponse(findBooking(id));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public BookingResponse cancel(Long id, Authentication authentication) {
        Booking booking = findBooking(id);
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin && !booking.getGuestUsername().equals(authentication.getName())) {
            throw new AccessDeniedException("You can cancel only your own bookings");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Booking " + id + " is already cancelled");
        }
        if (!isAdmin && !booking.getCheckIn().isAfter(LocalDate.now())) {
            throw new ConflictException("Booking can be cancelled only before the check-in date");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        return bookingMapper.toResponse(booking);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public BookingResponse confirm(Long id) {
        Booking booking = findBooking(id);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException("Only PENDING bookings can be confirmed, current status: "
                    + booking.getStatus());
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        return bookingMapper.toResponse(booking);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        bookingRepository.delete(findBooking(id));
    }

    private Booking findBooking(Long id) {
        return bookingRepository.findById(id).orElseThrow(() -> new NotFoundException("Booking", id));
    }
}
