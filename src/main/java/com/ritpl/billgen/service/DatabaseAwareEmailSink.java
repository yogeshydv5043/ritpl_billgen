package com.ritpl.billgen.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;
import com.ritpl.billgen.entity.EmailSetting;

/**
 * The mail transport the jobs actually call. It looks at the stored settings first, because an
 * administrator's change should take effect without a redeploy, and only then falls back to what the
 * deployment configured in {@code spring.mail.*}, and finally to recording the message.
 *
 * <p>Every path tells the truth about what happened: a message that could not be posted is reported as a
 * failure so the job marks the recipient failed, and a deployment with no transport says so in
 * {@link #describe()} rather than pretending. Being {@code @Primary} is also how the two
 * property-configured transports stay out of the way without either of them knowing about this class.</p>
 */
@Component
@Primary
public class DatabaseAwareEmailSink implements EmailSink {

    private final EmailSettingsService settings;
    private final ObjectProvider<SmtpEmailSink> configuredTransport;
    private final LoggingEmailSink recorder;

    public DatabaseAwareEmailSink(EmailSettingsService settings, ObjectProvider<SmtpEmailSink> configuredTransport,
            LoggingEmailSink recorder) {
        this.settings = settings; this.configuredTransport = configuredTransport; this.recorder = recorder;
    }

    @Override
    public void send(String recipient, String subject, String body) throws Exception {
        EmailSetting row = settings.current();
        if (!EmailSettingsService.canSend(row)) {
            SmtpEmailSink fallback = configuredTransport.getIfAvailable();
            if (fallback != null) fallback.send(recipient, subject, body);
            else recorder.send(recipient, subject, body);
            return;
        }
        String password = null;
        if (row.isAuthEnabled()) {
            password = settings.passwordFor(row);
            if (password == null) {
                // The message is not sent and the reason is not invented: the job records this failure
                // against the recipient, which is the honest outcome for "we have no credential to use".
                throw new IllegalStateException(settings.canStoreCredentials()
                        ? "The configured mail password is missing or cannot be read with the current key."
                        : "app.secrets.encryption-key is not configured, so the mail password cannot be read.");
            }
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalStateException("A notification was queued with no recipient address.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (row.getSenderEmail() != null) message.setFrom(row.getSenderEmail());
        if (row.getReplyToEmail() != null) message.setReplyTo(row.getReplyToEmail());
        message.setTo(recipient.trim());
        message.setSubject(subject);
        message.setText(body);
        EmailSettingsService.mailSender(row, password).send(message);
    }

    @Override
    public boolean isLive() {
        if (EmailSettingsService.canSend(settings.current())) return true;
        SmtpEmailSink fallback = configuredTransport.getIfAvailable();
        return fallback != null && fallback.isLive();
    }

    @Override
    public String describe() {
        EmailSetting row = settings.current();
        if (EmailSettingsService.canSend(row)) {
            return "SMTP " + row.getSmtpHost() + ":" + row.getSmtpPort() + " (from the email settings)";
        }
        if (row.getStatus() == com.ritpl.billgen.entity.ContentStatus.INACTIVE && row.getSmtpHost() != null) {
            SmtpEmailSink fallback = configuredTransport.getIfAvailable();
            if (fallback != null) return fallback.describe() + " - stored settings saved but not activated";
        }
        SmtpEmailSink fallback = configuredTransport.getIfAvailable();
        return fallback == null ? recorder.describe() : fallback.describe();
    }
}
