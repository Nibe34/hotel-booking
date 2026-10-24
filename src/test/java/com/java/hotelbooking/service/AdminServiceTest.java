package com.java.hotelbooking.service;

import com.java.hotelbooking.domain.dto.AdminStatsResponse;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.repository.BookingRepository;
import com.java.hotelbooking.repository.HotelRepository;
import com.java.hotelbooking.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    void stats_returnsCountsOfHotelsRoomsAndBookingsPerStatus() {
        when(hotelRepository.count()).thenReturn(3L);
        when(roomRepository.count()).thenReturn(12L);
        when(bookingRepository.countByStatus(BookingStatus.PENDING)).thenReturn(4L);
        when(bookingRepository.countByStatus(BookingStatus.CONFIRMED)).thenReturn(2L);
        when(bookingRepository.countByStatus(BookingStatus.CANCELLED)).thenReturn(1L);

        AdminStatsResponse stats = adminService.stats();

        assertThat(stats.hotels()).isEqualTo(3L);
        assertThat(stats.rooms()).isEqualTo(12L);
        assertThat(stats.bookingsByStatus())
                .containsEntry(BookingStatus.PENDING, 4L)
                .containsEntry(BookingStatus.CONFIRMED, 2L)
                .containsEntry(BookingStatus.CANCELLED, 1L);
    }

    @Test
    void stats_emptyDatabase_reportsZeroForEveryStatus() {
        AdminStatsResponse stats = adminService.stats();

        assertThat(stats.hotels()).isZero();
        assertThat(stats.rooms()).isZero();
        assertThat(stats.bookingsByStatus())
                .containsOnlyKeys(BookingStatus.values())
                .allSatisfy((status, count) -> assertThat(count).isZero());
        for (BookingStatus status : BookingStatus.values()) {
            verify(bookingRepository).countByStatus(status);
        }
    }
}
