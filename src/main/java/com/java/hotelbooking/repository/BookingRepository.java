package com.java.hotelbooking.repository;

import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.domain.model.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"room", "room.hotel"})
    List<Booking> findByGuestUsernameOrderByCheckInDesc(String guestUsername);

    @EntityGraph(attributePaths = {"room", "room.hotel"})
    List<Booking> findAllByOrderByCreatedAtDesc();

    @Query("""
            select count(b) > 0 from Booking b
            where b.room.id = :roomId
              and b.status in :activeStatuses
              and b.checkIn < :checkOut
              and b.checkOut > :checkIn
            """)
    boolean existsOverlapping(@Param("roomId") Long roomId,
                              @Param("checkIn") LocalDate checkIn,
                              @Param("checkOut") LocalDate checkOut,
                              @Param("activeStatuses") Collection<BookingStatus> activeStatuses);

    long countByStatus(BookingStatus status);

    /** PENDING bookings nobody confirmed in time, or whose check-in day has already come. Locked against a concurrent confirm. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from Booking b
            where b.status = com.java.hotelbooking.domain.enums.BookingStatus.PENDING
              and (b.createdAt < :createdBefore or b.checkIn <= :today)
            order by b.id
            """)
    List<Booking> findExpiredPending(@Param("createdBefore") Instant createdBefore,
                                     @Param("today") LocalDate today,
                                     Limit limit);

    /** CONFIRMED bookings starting on the given day that have not got the reminder for this "days before" yet. */
    @EntityGraph(attributePaths = {"room", "room.hotel"})
    @Query("""
            select b from Booking b
            where b.status = com.java.hotelbooking.domain.enums.BookingStatus.CONFIRMED
              and b.checkIn = :checkIn
              and not exists (select r.id from BookingReminder r
                              where r.bookingId = b.id and r.daysBefore = :daysBefore)
            order by b.id
            """)
    List<Booking> findDueForReminder(@Param("checkIn") LocalDate checkIn,
                                     @Param("daysBefore") int daysBefore,
                                     Limit limit);
}
