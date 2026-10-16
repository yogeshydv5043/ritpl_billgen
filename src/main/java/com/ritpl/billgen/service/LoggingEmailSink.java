package com.ritpl.billgen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default transport for deployments without an SMTP server: the message is rendered and recorded
 * against the recipient (so history, status and failures are all real) but nothing is posted.
 * The application never pretends a message was delivered.
 */
@Component
public class LoggingEmailSink implements EmailSink {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSink.class);

    @Override
    public boolean isLive() { return false; }

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("Email recorded for {} - {}", recipient, subject);
    }

    @Override
    public String describe() { return "Recording only (no mail transport configured)"; }
}
