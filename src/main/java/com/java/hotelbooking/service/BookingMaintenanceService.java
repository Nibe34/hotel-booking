package com.java.hotelbooking.service;

import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.enums.AuditAction;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.domain.event.BookingChangedEvent;
import com.java.hotelbooking.domain.mapper.BookingMapper;
import com.java.hotelbooking.domain.model.Booking;
import com.java.hotelbooking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Housekeeping of bookings, run by ExpirePendingBookingsJob. Idempotent: an expired booking becomes CANCELLED,
 * so a second run right after the first finds nothing to do.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingMaintenanceService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final AuditTrail auditTrail;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Cancels at most {@code batchSize} PENDING bookings that were not confirmed within {@code maxPendingAge}
     * or whose check-in day has come. Each one is audited (actor "system") and the guest gets an e-mail.
     *
     * @return how many bookings were cancelled
     */
    @Transactional
    public int expirePendingBookings(Duration maxPendingAge, int batchSize) {
        if (maxPendingAge.isNegative() || maxPendingAge.isZero()) {
            throw new IllegalArgumentException("maxPendingAge must be positive, was " + maxPendingAge);
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive, was " + batchSize);
        }

        List<Booking> expired = bookingRepository.findExpiredPending(clock.instant().minus(maxPendingAge),
                LocalDate.now(clock), Limit.of(batchSize));
        for (Booking booking : expired) {
            booking.setStatus(BookingStatus.CANCELLED);
            BookingResponse response = bookingMapper.toResponse(booking);
            auditTrail.record(response, AuditAction.CANCEL);
            eventPublisher.publishEvent(new BookingChangedEvent(response, AuditAction.CANCEL));
        }
        if (!expired.isEmpty()) {
            log.info("Expired {} pending booking(s): {}", expired.size(), expired.stream().map(Booking::getId).toList());
        }
        return expired.size();
    }
}
