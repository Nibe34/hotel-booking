package com.java.hotelbooking.aspect;

import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.enums.AuditAction;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.domain.mapper.BookingMapper;
import com.java.hotelbooking.domain.model.Booking;
import com.java.hotelbooking.domain.model.BookingAuditEntry;
import com.java.hotelbooking.domain.model.Hotel;
import com.java.hotelbooking.domain.model.Room;
import com.java.hotelbooking.repository.BookingAuditRepository;
import com.java.hotelbooking.repository.BookingRepository;
import com.java.hotelbooking.repository.RoomRepository;
import com.java.hotelbooking.service.AuditTrail;
import com.java.hotelbooking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The same @AuditBooking method {@code confirm} is called in two ways through a real Spring AOP proxy
 * (built with AspectJProxyFactory, no application context):
 * <ul>
 *     <li>from outside - the call goes through the proxy and the audit advice runs;</li>
 *     <li>from {@code confirmBatch} via {@code this.confirm(id)} - the call stays inside the target object,
 *     the advice silently does not run, and no error is reported.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SelfInvocationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookingMapper bookingMapper;

    @Mock
    private BookingAuditRepository auditRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BookingService proxy;

    @BeforeEach
    void setUp() {
        BookingService target = new BookingService(bookingRepository, roomRepository, bookingMapper, CLOCK,
                eventPublisher);
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new BookingAuditAspect(new AuditTrail(auditRepository, CLOCK)));
        lenient().when(auditRepository.save(any(BookingAuditEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        proxy = factory.getProxy();

        when(bookingRepository.findById(any())).thenAnswer(inv -> Optional.of(pending(inv.getArgument(0))));
        when(bookingMapper.toResponse(any(Booking.class))).thenAnswer(inv -> response(inv.getArgument(0)));
    }

    private Booking pending(Long id) {
        Hotel hotel = new Hotel();
        hotel.setId(1L);
        Room room = new Room();
        room.setId(1L);
        room.setHotel(hotel);
        Booking booking = new Booking();
        booking.setId(id);
        booking.setRoom(room);
        booking.setGuestUsername("bob");
        booking.setCheckIn(LocalDate.of(2026, 11, 1));
        booking.setCheckOut(LocalDate.of(2026, 11, 2));
        booking.setStatus(BookingStatus.PENDING);
        return booking;
    }

    private BookingResponse response(Booking booking) {
        return new BookingResponse(booking.getId(), 1L, "101", 1L, "Grand", booking.getGuestUsername(),
                booking.getCheckIn(), booking.getCheckOut(), 1, BigDecimal.TEN, booking.getStatus(), null);
    }

    @Test
    void externalCallThroughProxy_runsAuditAdvice() {
        assertThat(AopUtils.isAopProxy(proxy)).isTrue();

        BookingResponse result = proxy.confirm(1L);

        assertThat(result.status()).isEqualTo(BookingStatus.CONFIRMED);
        ArgumentCaptor<BookingAuditEntry> captor = ArgumentCaptor.forClass(BookingAuditEntry.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getBookingId()).isEqualTo(1L);
        assertThat(captor.getValue().getAction()).isEqualTo(AuditAction.CONFIRM);
    }

    @Test
    void selfInvocationFromConfirmBatch_silentlySkipsAuditAdvice() {
        List<BookingResponse> result = proxy.confirmBatch(List.of(1L, 2L));

        // the business logic of confirm() did run for both bookings...
        assertThat(result).extracting(BookingResponse::status)
                .containsExactly(BookingStatus.CONFIRMED, BookingStatus.CONFIRMED);
        // ...but the advice did not: this.confirm(id) bypasses the proxy, and no exception tells us so
        verify(auditRepository, never()).save(any());
    }
}
