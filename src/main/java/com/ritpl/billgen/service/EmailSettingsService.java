package com.ritpl.billgen.service;

import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.EmailSecurityProtocol;
import com.ritpl.billgen.entity.EmailSetting;
import com.ritpl.billgen.repository.EmailSettingRepository;
import com.ritpl.billgen.security.TenantScope;
import jakarta.mail.internet.MimeMessage;

/**
 * The mail transport as data, so an administrator can point the application at their own SMTP server
 * without a redeploy. Two rules hold it together.
 *
 * <p>The password is write-only. It is sealed with {@link SecretCipher} at save time, never returned by
 * any read path, and an empty submission means "unchanged" rather than "cleared" - a form that cannot
 * show the value must not be the thing that removes it. Nothing here logs or echoes a credential,
 * including on a failed test send, which is where the temptation to paste the provider's reply into the
 * screen is strongest.</p>
 *
 * <p>Absent or inactive settings are not an error: the application then keeps using {@code spring.mail.*}
 * and records instead of sending, which is what the existing tests and any deployment without a mail
 * server already rely on.</p>
 */
@Service
public class EmailSettingsService {

    private final EmailSettingRepository settings;
    private final SecretCipher cipher;
    private final AuditService audit;

    public EmailSettingsService(EmailSettingRepository settings, SecretCipher cipher, AuditService audit) {
        this.settings = settings; this.cipher = cipher; this.audit = audit;
    }

    /** The stored row, or a detached default that says "not configured". */
    @Transactional(readOnly = true)
    public EmailSetting current() {
        return settings.findById(EmailSetting.SINGLETON_ID).map(EmailSetting::attached).orElseGet(EmailSetting::defaults);
    }

    @Transactional(readOnly = true)
    public View view() {
        EmailSetting row = current();
        return new View(row.getSmtpHost(), row.getSmtpPort(), row.getSmtpUsername(), row.hasCredential(),
                row.getSecretSetAt(), row.isAuthEnabled(), row.getSecurityProtocol(), row.getTimeoutMs(),
                row.getSenderName(), row.getSenderEmail(), row.getReplyToEmail(), row.getStatus(),
                cipher.isAvailable(), canSend(row), describe(row),
                row.hasCredential() && cipher.open(row.getSmtpSecret()) == null);
    }

    @Transactional
    public EmailSetting save(TenantScope scope, EmailSettingsCommand command) {
        ConfigurationRules.requireAdmin(scope);
        EmailSetting row = settings.findById(EmailSetting.SINGLETON_ID).map(EmailSetting::attached)
                .orElseGet(EmailSetting::defaults);
        boolean created = row.getCreatedAt() == null;
        String host = ConfigurationRules.host(command.smtpHost(), "SMTP host");
        Integer port = command.smtpPort() == null ? null
                : ConfigurationRules.bounded(command.smtpPort(), 587, 1, 65535, "SMTP port");
        String username = ConfigurationRules.optional(command.smtpUsername(), 190, "SMTP username");
        String senderEmail = ConfigurationRules.email(command.senderEmail(), 190, "From address");
        String replyTo = ConfigurationRules.email(command.replyToEmail(), 190, "Reply-to address");
        int timeout = ConfigurationRules.bounded(command.timeoutMs(), row.getTimeoutMs(), 1_000, 120_000, "Timeout");
        boolean auth = command.authEnabled() == null || command.authEnabled();
        EmailSecurityProtocol protocol = command.securityProtocol() == null
                ? row.getSecurityProtocol() : command.securityProtocol();
        // The form always submits the username it is showing, so a submission with authentication on and
        // no username is an instruction to change that, not an oversight to paper over with the stored one.
        if (auth && (username == null || username.isBlank())) {
            throw new IllegalArgumentException("An SMTP username is required when authentication is on.");
        }
        if (!auth && protocol == EmailSecurityProtocol.SSL_TLS) {
            throw new IllegalArgumentException("An unauthenticated secure connection is not a thing; "
                    + "either provide a username and password or use no encryption on port 25.");
        }

        Map<String, Object> before = created ? null : snapshot(row);
        boolean changed = false;

        if (host != null && !host.equals(row.getSmtpHost())) { row.setSmtpHost(host); changed = true; }
        if (port != null && !port.equals(row.getSmtpPort())) { row.setSmtpPort(port); changed = true; }
        if (username != null && !username.equals(row.getSmtpUsername())) { row.setSmtpUsername(username); changed = true; }
        // A blank submission is not a clearing: the only way to lose a credential is to seal a new one.
        if (command.password() != null && !command.password().isBlank()) {
            row.setSmtpSecret(cipher.seal(command.password().trim()));
            row.markSecretSet(Instant.now());
            changed = true;
        }
        if (auth != row.isAuthEnabled()) { row.setAuthEnabled(auth); changed = true; }
        if (protocol != row.getSecurityProtocol()) { row.setSecurityProtocol(protocol); changed = true; }
        if (timeout != row.getTimeoutMs()) { row.setTimeoutMs(timeout); changed = true; }
        if (senderEmail != null && !senderEmail.equals(row.getSenderEmail())) { row.setSenderEmail(senderEmail); changed = true; }
        if (replyTo != null && !replyTo.equals(row.getReplyToEmail())) { row.setReplyToEmail(replyTo); changed = true; }
        String senderName = ConfigurationRules.optional(command.senderName(), 150, "From name");
        if (senderName != null && !senderName.equals(row.getSenderName())) { row.setSenderName(senderName); changed = true; }
        ContentStatus status = command.status() == null ? row.getStatus() : command.status();
        if (status != row.getStatus()) { row.setStatus(status); changed = true; }
        // Activating an incomplete transport would only move the failure into a background job, where
        // nobody is watching - so the row is saved as inactive and the page says what is still missing.
        if (status == ContentStatus.ACTIVE && !complete(row)) {
            throw new IllegalArgumentException("A host, port and from address are all required before the "
                    + "transport can be activated.");
        }

        if (created) row.recordAuthor("administrator"); else if (changed) row.recordEditor("administrator");
        EmailSetting saved = settings.save(row);
        if (!created) {
            audit.record(AuditEventCommand.success(null, "UPDATE", "EMAIL_SETTINGS", "Email settings",
                    EmailSetting.SINGLETON_ID,
                    changed ? "Administrator updated the mail transport (" + describe(saved) + ")."
                            : "Email settings submitted with no change.",
                    before, snapshot(saved)));
        }
        return EmailSetting.attached(saved);
    }

    /**
     * Proves the transport with a real message. The result says what happened, in the provider's own
     * words where they are safe to show, and never more than that.
     */
    @Transactional
    public TestResult test(TenantScope scope, String recipient) {
        ConfigurationRules.requireAdmin(scope);
        String to = ConfigurationRules.email(recipient, 190, "Recipient address");
        if (to == null) throw new IllegalArgumentException("Enter the address to send the test message to.");
        EmailSetting row = current();
        if (row.getStatus() != ContentStatus.ACTIVE || !complete(row)) {
            return new TestResult(false, "These settings are not active and complete yet: " + describe(row) + ".");
        }
        String password = null;
        if (row.isAuthEnabled()) {
            password = cipher.open(row.getSmtpSecret());
            if (password == null) {
                return new TestResult(false, cipher.isAvailable()
                        ? "No password is stored for this server. Enter one and save before testing."
                        : "No encryption key is configured, so no password can be stored or read. "
                                + "Set app.secrets.encryption-key and enter the password again.");
            }
        }
        try {
            JavaMailSenderImpl sender = mailSender(row, password);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(row.getSenderEmail(), row.getSenderName() == null ? row.getSenderEmail() : row.getSenderName());
            if (row.getReplyToEmail() != null) helper.setReplyTo(row.getReplyToEmail());
            helper.setTo(to);
            helper.setSubject("Mail test from " + (row.getSenderName() == null ? "the billing application" : row.getSenderName()));
            helper.setText("This is a test message from the billing application's mail settings.\n\n"
                    + "Server: " + row.getSmtpHost() + ":" + row.getSmtpPort() + " ("
                    + row.getSecurityProtocol().getDisplayName() + ")\n"
                    + "Sender: " + row.getSenderEmail() + "\n\n"
                    + "If you received this, the transport is configured correctly.", false);
            sender.send(message);
            audit.record(AuditEventCommand.success(null, "TEST", "EMAIL_SETTINGS", "Email settings",
                    EmailSetting.SINGLETON_ID, "Test message accepted for " + to + ".",
                    null, Map.of("result", "accepted", "recipient", to)));
            return new TestResult(true, "The test message was accepted for " + to + ".");
        } catch (Exception failed) {
            String reason = explain(failed);
            audit.record(AuditEventCommand.failure(null, "TEST", "EMAIL_SETTINGS", "Email settings",
                    EmailSetting.SINGLETON_ID, "Test message to " + to + " failed: " + reason,
                    Map.of("result", "failed", "recipient", to, "reason", reason)));
            return new TestResult(false, "The test message was not sent: " + reason);
        }
    }

    /** A sender built fresh from the stored row, so a settings change takes effect for the next message. */
    static JavaMailSenderImpl mailSender(EmailSetting row, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(row.getSmtpHost());
        sender.setPort(row.getSmtpPort() == null ? 25 : row.getSmtpPort());
        sender.setDefaultEncoding("UTF-8");
        if (row.getSmtpUsername() != null) sender.setUsername(row.getSmtpUsername());
        if (password != null) sender.setPassword(password);
        boolean secure = row.getSecurityProtocol() == EmailSecurityProtocol.SSL_TLS;
        String prefix = secure ? "mail.smtps." : "mail.smtp.";
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", secure ? "smtps" : "smtp");
        for (String suffix : new String[] { "connectiontimeout", "timeout", "writetimeout" }) {
            props.put(prefix + suffix, String.valueOf(row.getTimeoutMs()));
        }
        if (row.getSecurityProtocol() == EmailSecurityProtocol.START_TLS) props.put(prefix + "starttls.enable", "true");
        if (secure) props.put(prefix + "ssl.enable", "true");
        return sender;
    }

    /** The plaintext credential for a row, or null when none is stored or it cannot be read. */
    String passwordFor(EmailSetting row) { return cipher.open(row.getSmtpSecret()); }

    boolean canStoreCredentials() { return cipher.isAvailable(); }

    /** Whether a row is fit to be handed to {@link #mailSender}. The entity's own check is looser. */
    private static boolean complete(EmailSetting row) {
        return row.isUsable() && row.getSmtpPort() != null
                && row.getSenderEmail() != null && !row.getSenderEmail().isBlank()
                && (!row.isAuthEnabled() || row.getSmtpUsername() != null);
    }

    /** True when this row, rather than {@code spring.mail.*}, is what will carry the next message. */
    static boolean canSend(EmailSetting row) {
        return row.getStatus() == ContentStatus.ACTIVE && complete(row);
    }

    /** How the settings read to a human - on this screen, and as the transport label elsewhere. */
    private String describe(EmailSetting row) {
        if (row.getSmtpHost() == null) return "not configured, so messages are recorded rather than sent";
        if (row.getStatus() != ContentStatus.ACTIVE) return "saved but not activated, so messages are recorded";
        if (!complete(row)) return "activated but incomplete: a host, port and from address are required";
        return "SMTP " + row.getSmtpHost() + ":" + row.getSmtpPort() + " ("
                + row.getSecurityProtocol().getDisplayName() + ", from " + row.getSenderEmail() + ")";
    }

    /** The provider's first line if there is one, with nothing that looks like a credential in it. */
    private static String explain(Throwable failed) {
        Throwable cause = failed;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        if (cause instanceof java.net.UnknownHostException) return "the server name could not be resolved";
        if (cause instanceof java.net.SocketTimeoutException) return "the server did not answer in time";
        if (cause instanceof java.net.ConnectException) return "the server refused the connection";
        String message = cause.getMessage();
        if (message != null && message.toLowerCase().contains("authent")) {
            return "the username or password was refused by the server";
        }
        String text = cause.getClass().getSimpleName();
        if (message != null && !message.isBlank()) {
            String first = message.lines().findFirst().orElse("");
            if (first.length() > 160) first = first.substring(0, 160) + "...";
            text = text + " (" + first.replaceAll("(?i)(password|passcmd|auth[a-z]*=)[^,\s]*", "$1[hidden]") + ")";
        }
        return text;
    }

    /** A snapshot with the credential reduced to "set" or "none", for the audit trail. */
    private static Map<String, Object> snapshot(EmailSetting row) {
        return Map.of("smtpHost", String.valueOf(row.getSmtpHost()), "smtpPort", String.valueOf(row.getSmtpPort()),
                "username", String.valueOf(row.getSmtpUsername()), "status", row.getStatus().name(),
                "protocol", row.getSecurityProtocol().name(), "sender", String.valueOf(row.getSenderEmail()),
                "password", row.hasCredential() ? "set" : "none");
    }

    /** What the settings screen shows. There is no password field in it, on purpose. */
    public record View(String smtpHost, Integer smtpPort, String smtpUsername, boolean passwordSet,
            Instant passwordSetAt, boolean authEnabled, EmailSecurityProtocol securityProtocol, Integer timeoutMs,
            String senderName, String senderEmail, String replyToEmail, ContentStatus status,
            boolean credentialStorageAvailable, boolean transportLive, String transportSummary,
            boolean passwordUnreadable) {

        public String passwordHint() {
            if (!passwordSet) return "No password stored.";
            if (passwordUnreadable) return "A password is stored but cannot be read with the current encryption key.";
            return "Stored" + (passwordSetAt == null ? "" : " on " + java.time.LocalDate.from(passwordSetAt.atZone(java.time.ZoneOffset.UTC)));
        }
    }

    /** The outcome of a test send, phrased for the page rather than the log. */
    public record TestResult(boolean sent, String message) { }
}
