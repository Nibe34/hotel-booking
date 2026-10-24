package com.java.hotelbooking.controller;

import com.java.hotelbooking.domain.dto.BookingCreateRequest;
import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.service.BookingService;
import com.java.hotelbooking.web.annotation.CurrentUsername;
import com.java.hotelbooking.web.annotation.PostCreated;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostCreated
    public BookingResponse create(@Valid @RequestBody BookingCreateRequest request, @CurrentUsername String username) {
        return bookingService.create(request, username);
    }

    @GetMapping("/my")
    public List<BookingResponse> myBookings(@CurrentUsername String username) {
        return bookingService.listMine(username);
    }

    @GetMapping
    public List<BookingResponse> listAll() {
        return bookingService.listAll();
    }

    @GetMapping("/{id}")
    public BookingResponse getById(@PathVariable Long id) {
        return bookingService.getById(id);
    }

    @PatchMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id, Authentication authentication) {
        return bookingService.cancel(id, authentication);
    }

    @PatchMapping("/{id}/confirm")
    public BookingResponse confirm(@PathVariable Long id) {
        return bookingService.confirm(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        bookingService.delete(id);
    }
}
