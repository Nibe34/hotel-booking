package com.java.hotelbooking.repository;

import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.domain.model.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    @EntityGraph(attributePaths = "hotel")
    List<Room> findByHotelIdOrderByRoomNumber(Long hotelId);

    // Serializes concurrent bookings of the same room
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r join fetch r.hotel where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    boolean existsByHotelIdAndRoomNumber(Long hotelId, String roomNumber);

    @Query("""
            select r from Room r join fetch r.hotel h
            where r.capacity >= :guests
              and not exists (
                  select b from Booking b
                  where b.room = r
                    and b.status in :activeStatuses
                    and b.checkIn < :checkOut
                    and b.checkOut > :checkIn)
            order by h.city, h.name, r.pricePerNight
            """)
    List<Room> findAvailable(@Param("checkIn") LocalDate checkIn,
                             @Param("checkOut") LocalDate checkOut,
                             @Param("guests") int guests,
                             @Param("activeStatuses") Collection<BookingStatus> activeStatuses);
}
