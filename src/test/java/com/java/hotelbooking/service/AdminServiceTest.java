package com.java.hotelbooking.service;

import com.java.hotelbooking.domain.dto.AdminStatsResponse;
import com.java.hotelbooking.domain.dto.BookingAuditResponse;
import com.java.hotelbooking.domain.enums.AuditAction;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.domain.mapper.BookingAuditMapper;
import com.java.hotelbooking.domain.model.BookingAuditEntry;
import com.java.hotelbooking.repository.BookingAuditRepository;
import com.java.hotelbooking.repository.BookingRepository;
import com.java.hotelbooking.repository.HotelRepository;
import com.java.hotelbooking.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    @Mock
    private BookingAuditRepository auditRepository;

    @Mock
    private BookingAuditMapper auditMapper;

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

    private BookingAuditEntry entry(long id, long bookingId, AuditAction action) {
        BookingAuditEntry entry = new BookingAuditEntry();
        entry.setId(id);
        entry.setBookingId(bookingId);
        entry.setAction(action);
        return entry;
    }

    private BookingAuditResponse response(BookingAuditEntry entry) {
        return new BookingAuditResponse(entry.getId(), entry.getBookingId(), entry.getAction(), null, null, null);
    }

    @Test
    void audit_forBooking_returnsItsTrailOldestFirst() {
        BookingAuditEntry created = entry(1L, 5L, AuditAction.CREATE);
        BookingAuditEntry confirmed = entry(2L, 5L, AuditAction.CONFIRM);
        when(auditRepository.findByBookingIdOrderByIdAsc(5L)).thenReturn(List.of(created, confirmed));
        when(auditMapper.toResponse(any(BookingAuditEntry.class))).thenAnswer(inv -> response(inv.getArgument(0)));

        List<BookingAuditResponse> result = adminService.audit(5L);

        assertThat(result).extracting(BookingAuditResponse::action)
                .containsExactly(AuditAction.CREATE, AuditAction.CONFIRM);
        verify(auditRepository, never()).findTop100ByOrderByIdDesc();
    }

    @Test
    void audit_withoutBookingId_returnsLatestEntries() {
        when(auditRepository.findTop100ByOrderByIdDesc()).thenReturn(List.of(entry(9L, 1L, AuditAction.CANCEL)));
        when(auditMapper.toResponse(any(BookingAuditEntry.class))).thenAnswer(inv -> response(inv.getArgument(0)));

        List<BookingAuditResponse> result = adminService.audit(null);

        assertThat(result).extracting(BookingAuditResponse::id).containsExactly(9L);
    }
}
