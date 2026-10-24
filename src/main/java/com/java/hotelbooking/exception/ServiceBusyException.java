package com.java.hotelbooking.exception;

import lombok.Getter;

/** The server is temporarily overloaded or a dependency is too slow; the client should retry later (503). */
@Getter
public class ServiceBusyException extends RuntimeException {

    private final long retryAfterSeconds;

    public ServiceBusyException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
