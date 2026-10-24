package com.java.hotelbooking.service;

import com.java.hotelbooking.domain.enums.AuditAction;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.domain.enums.RoomType;
import com.java.hotelbooking.domain.event.BookingChangedEvent;
import com.java.hotelbooking.domain.event.CheckInReminderEvent;
import com.java.hotelbooking.domain.mapper.BookingMapperImpl;
import com.java.hotelbooking.domain.model.Booking;
import com.java.hotelbooking.domain.model.BookingAuditEntry;
import com.java.hotelbooking.domain.model.Hotel;
import com.java.hotelbooking.domain.model.Room;
import com.java.hotelbooking.repository.BookingAuditRepository;
import com.java.hotelbooking.repository.BookingReminderRepository;
import com.java.hotelbooking.repository.BookingRepository;
import com.java.hotelbooking.repository.HotelRepository;
import com.java.hotelbooking.repository.RoomRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The services behind the Quartz jobs on a real database (H2) and real transactions: every call commits on
 * its own, exactly like a job run. Running the logic twice in a row must not change anything the second time.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({BookingMaintenanceService.class, CheckInReminderService.class, AuditTrail.class, BookingMapperImpl.class,
        JobServicesIntegrationTest.FixedClock.class})
@RecordApplicationEvents
class JobServicesIntegrationTest {

    static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
    static final Instant NOW = Instant.parse("2026-10-24T09:00:00Z");
    static final LocalDate TODAY = LocalDate.ofInstant(NOW, KYIV);

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, KYIV);
        }
    }

    @Autowired
    private BookingMaintenanceService maintenanceService;

    @Autowired
    private CheckInReminderService reminderService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingAuditRepository auditRepository;

    @Autowired
    private BookingReminderRepository reminderRepository;

    @Autowired
    private ApplicationEvents events;

    private Room room;

    @BeforeEach
    void createRoom() {
        Hotel hotel = new Hotel();
        hotel.setName("Grand");
        hotel.setCity("Kyiv");
        hotel.setAddress("Main 1");
        hotel.setStars(4);
        hotel = hotelRepository.save(hotel);
        room = new Room();
        room.setHotel(hotel);
        room.setRoomNumber("101");
        room.setType(RoomType.DOUBLE);
        room.setCapacity(2);
        room.setPricePerNight(new BigDecimal("1000.00"));
        room = roomRepository.save(room);
    }

    @AfterEach
    void cleanUp() {
        reminderRepository.deleteAll();
        auditRepository.deleteAll();
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    private Booking booking(BookingStatus status, LocalDate checkIn, Instant createdAt) {
        Booking booking = new Booking();
        booking.setRoom(room);
        booking.setGuestUsername("bob");
        booking.setCheckIn(checkIn);
        booking.setCheckOut(checkIn.plusDays(2));
        booking.setGuestsCount(1);
        booking.setTotalPrice(new BigDecimal("2000.00"));
        booking.setStatus(status);
        booking.setCreatedAt(createdAt);
        return bookingRepository.save(booking);
    }

    private BookingStatus statusOf(Booking booking) {
        return bookingRepository.findById(booking.getId()).orElseThrow().getStatus();
    }

    // ---------- expire pending bookings ----------

    @Test
    void expirePending_cancelsOnlyExpired_andSecondRunChangesNothing() {
        Booking stale = booking(BookingStatus.PENDING, TODAY.plusDays(10), NOW.minus(Duration.ofHours(25)));
        Booking arrivingToday = booking(BookingStatus.PENDING, TODAY, NOW.minus(Duration.ofMinutes(5)));
        Booking fresh = booking(BookingStatus.PENDING, TODAY.plusDays(10), NOW.minus(Duration.ofHours(1)));
        Booking confirmed = booking(BookingStatus.CONFIRMED, TODAY, NOW.minus(Duration.ofDays(3)));

        int firstRun = maintenanceService.expirePendingBookings(Duration.ofHours(24), 100);
        int secondRun = maintenanceService.expirePendingBookings(Duration.ofHours(24), 100);

        assertThat(firstRun).isEqualTo(2);
        assertThat(secondRun).isZero();
        assertThat(statusOf(stale)).isEqualTo(BookingStatus.CANCELLED);
        assertThat(statusOf(arrivingToday)).isEqualTo(BookingStatus.CANCELLED);
        assertThat(statusOf(fresh)).isEqualTo(BookingStatus.PENDING);
        assertThat(statusOf(confirmed)).isEqualTo(BookingStatus.CONFIRMED);
        // one audit row and one guest notification per cancelled booking - not one per run
        assertThat(auditRepository.findAll()).hasSize(2)
                .allSatisfy(entry -> {
                    assertThat(entry.getAction()).isEqualTo(AuditAction.CANCEL);
                    assertThat(entry.getActor()).isEqualTo(AuditTrail.SYSTEM_ACTOR);
                })
                .extracting(BookingAuditEntry::getBookingId)
                .containsExactlyInAnyOrder(stale.getId(), arrivingToday.getId());
        assertThat(events.stream(BookingChangedEvent.class)).hasSize(2);
    }

    @Test
    void expirePending_respectsBatchSize_restIsLeftForTheNextRun() {
        for (int i = 0; i < 3; i++) {
            booking(BookingStatus.PENDING, TODAY.plusDays(10 + i * 3), NOW.minus(Duration.ofDays(2)));
        }

        assertThat(maintenanceService.expirePendingBookings(Duration.ofHours(24), 2)).isEqualTo(2);
        assertThat(maintenanceService.expirePendingBookings(Duration.ofHours(24), 2)).isEqualTo(1);
        assertThat(maintenanceService.expirePendingBookings(Duration.ofHours(24), 2)).isZero();
    }

    @Test
    void expirePending_invalidParameters_failExplicitly() {
        assertThatThrownBy(() -> maintenanceService.expirePendingBookings(Duration.ZERO, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxPendingAge must be positive, was PT0S");
        assertThatThrownBy(() -> maintenanceService.expirePendingBookings(Duration.ofHours(1), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize must be positive, was 0");
    }

    // ---------- check-in reminders ----------

    @Test
    void reminders_sentOncePerBookingAndDaysBefore_secondRunSendsNothing() {
        Booking tomorrow = booking(BookingStatus.CONFIRMED, TODAY.plusDays(1), NOW.minus(Duration.ofDays(5)));
        booking(BookingStatus.PENDING, TODAY.plusDays(1), NOW.minus(Duration.ofHours(1)));     // not confirmed
        booking(BookingStatus.CONFIRMED, TODAY.plusDays(2), NOW.minus(Duration.ofDays(5)));   // another day

        int firstRun = reminderService.sendReminders(1, 100);
        int secondRun = reminderService.sendReminders(1, 100);

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isZero();
        assertThat(reminderRepository.findAll()).singleElement().satisfies(reminder -> {
            assertThat(reminder.getBookingId()).isEqualTo(tomorrow.getId());
            assertThat(reminder.getDaysBefore()).isEqualTo(1);
            assertThat(reminder.getSentAt()).isEqualTo(NOW);
        });
        assertThat(events.stream(CheckInReminderEvent.class)).singleElement()
                .satisfies(event -> assertThat(event.booking().id()).isEqualTo(tomorrow.getId()));
    }

    @Test
    void reminders_weekBeforeAndDayBefore_areIndependent() {
        Booking inAWeek = booking(BookingStatus.CONFIRMED, TODAY.plusDays(7), NOW.minus(Duration.ofDays(5)));

        assertThat(reminderService.sendReminders(7, 100)).isEqualTo(1);
        assertThat(reminderService.sendReminders(7, 100)).isZero();
        assertThat(reminderService.sendReminders(1, 100)).isZero(); // check-in is not tomorrow

        assertThat(reminderRepository.findAll()).extracting(r -> r.getBookingId() + "/" + r.getDaysBefore())
                .containsExactly(inAWeek.getId() + "/7");
    }

    @Test
    void reminders_invalidParameters_failExplicitly() {
        assertThatThrownBy(() -> reminderService.sendReminders(-1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("daysBefore must not be negative, was -1");
        assertThatThrownBy(() -> reminderService.sendReminders(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
