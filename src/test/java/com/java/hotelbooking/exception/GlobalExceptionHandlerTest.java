package com.java.hotelbooking.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bookings/42");

    @Test
    void handleNotFound_returns404WithMessageAndPath() {
        ResponseEntity<ApiError> response = handler.handleNotFound(new NotFoundException("Booking", 42L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("Not Found");
        assertThat(response.getBody().message()).isEqualTo("Booking with id 42 not found");
        assertThat(response.getBody().path()).isEqualTo("/api/bookings/42");
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().fieldErrors()).isNull();
    }

    @Test
    void handleConflict_returns409WithMessage() {
        ResponseEntity<ApiError> response = handler.handleConflict(new ConflictException("already booked"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("already booked");
    }

    @Test
    void handleBadRequest_returns400WithMessage() {
        ResponseEntity<ApiError> response = handler.handleBadRequest(new BadRequestException("bad dates"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("bad dates");
    }

    @Test
    void handleValidation_collectsFirstErrorPerField() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "checkIn", "must not be null"));
        bindingResult.addError(new FieldError("request", "guestsCount", "must be greater than or equal to 1"));
        bindingResult.addError(new FieldError("request", "checkIn", "must be a date in the present or in the future"));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiError> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Validation failed");
        assertThat(response.getBody().fieldErrors())
                .containsExactly(
                        Map.entry("checkIn", "must not be null"),
                        Map.entry("guestsCount", "must be greater than or equal to 1"));
    }

    @Test
    void handleRateLimit_returns429WithRetryAfterHeader() {
        ResponseEntity<ApiError> response =
                handler.handleRateLimit(new RateLimitExceededException("Too many calls", 42), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Too many calls");
    }

    @Test
    void handleServiceBusy_returns503WithRetryAfterHeader() {
        ResponseEntity<ApiError> response =
                handler.handleServiceBusy(new ServiceBusyException("try again later", 5), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("try again later");
    }

    @Test
    void handleAccessDenied_returns403WithPrefixedMessage() {
        ResponseEntity<ApiError> response =
                handler.handleAccessDenied(new AccessDeniedException("You can cancel only your own bookings"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo("AccessDeniedException: You can cancel only your own bookings");
    }
}
