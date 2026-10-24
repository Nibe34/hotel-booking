package com.java.hotelbooking.service.notification;

import com.java.hotelbooking.integration.Email;
import com.java.hotelbooking.integration.MailGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Fire-and-forget: the caller gets control back at once, the mail is sent on the async executor.
 * A failure there is reported to the AsyncUncaughtExceptionHandler (log + metric).
 */
@Component
@RequiredArgsConstructor
public class GuestNotifier {

    private final MailGateway mailGateway;

    @Async
    public void send(Email email) {
        mailGateway.send(email);
    }
}
