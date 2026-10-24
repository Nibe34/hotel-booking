package com.java.hotelbooking.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A check-in reminder that was already sent; at most one per booking and "days before" value. */
@Entity
@Table(name = "booking_reminders",
        uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id", "days_before"}))
@Getter
@Setter
@NoArgsConstructor
public class BookingReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "days_before", nullable = false)
    private Integer daysBefore;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
}
