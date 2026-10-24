package com.java.hotelbooking.integration;

import com.java.hotelbooking.configuration.SimulationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pretends to talk to an SMTP server: takes about a second and fails for recipients configured as unreachable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SimulatedMailGateway implements MailGateway {

    private final SlowCallSimulator slowCall;
    private final SimulationProperties properties;

    @Override
    public void send(Email email) {
        slowCall.simulate("mail", properties.mailDelay());
        if (properties.unreachableRecipients().contains(email.to())) {
            throw new MailDeliveryException("Mailbox of " + email.to() + " is unreachable");
        }
        log.info("Mail sent to {}: {}", email.to(), email.subject());
    }
}
