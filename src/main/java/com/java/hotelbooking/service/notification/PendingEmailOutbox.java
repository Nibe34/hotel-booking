package com.java.hotelbooking.service.notification;

import com.java.hotelbooking.integration.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * E-mails the executor refused to take (pool and queue full) wait here and are resubmitted periodically,
 * so an overload delays notifications instead of losing them or failing the booking request.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingEmailOutbox {

    private final GuestNotifier notifier;
    private final Queue<Email> pending = new ConcurrentLinkedQueue<>();

    public void add(Email email) {
        pending.add(email);
    }

    public int size() {
        return pending.size();
    }

    @Scheduled(fixedDelayString = "${hotel.async.outbox-retry-delay:PT10S}")
    public void retryPending() {
        int attempts = pending.size();
        int resubmitted = 0;
        for (int i = 0; i < attempts; i++) {
            Email email = pending.poll();
            if (email == null) {
                break;
            }
            try {
                notifier.send(email);
                resubmitted++;
            } catch (TaskRejectedException e) {
                // still overloaded: keep the mail and try again next time
                pending.add(email);
                break;
            }
        }
        if (resubmitted > 0) {
            log.info("Resubmitted {} pending e-mail(s), {} still waiting", resubmitted, pending.size());
        }
    }
}
