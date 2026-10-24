package com.java.hotelbooking.integration;

/** Outgoing mail server. */
public interface MailGateway {

    void send(Email email);
}
