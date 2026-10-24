package com.java.hotelbooking.repository;

import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.domain.model.Booking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
