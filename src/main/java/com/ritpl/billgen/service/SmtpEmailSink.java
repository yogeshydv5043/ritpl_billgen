package com.ritpl.billgen.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Real delivery, enabled by setting {@code spring.mail.host}. This is the only place that touches a
 * mail server; the from address is configuration, never user input.
 */
@Component
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class SmtpEmailSink implements EmailSink {
    private final JavaMailSender mail;
    private final String from;

    public SmtpEmailSink(JavaMailSender mail, @Value("${app.mail.from:no-reply@billgen.local}") String from) {
        this.mail = mail; this.from = from;
    }

    @Override
    public void send(String recipient, String subject, String body) throws Exception {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(recipient); message.setSubject(subject); message.setText(body);
        mail.send(message);
    }

    @Override
    public boolean isLive() { return true; }

    @Override
    public String describe() { return "SMTP (" + from + ")"; }
}
