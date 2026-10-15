package com.ritpl.billgen.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

/**
 * How this installation sends mail, chosen by an administrator instead of by an operator editing a
 * properties file. The password is never held in clear text: {@code smtp_secret} contains the sealed
 * envelope produced by {@link com.ritpl.billgen.service.SecretCipher}, and no read path returns it.
 *
 * <p>An unset host means the row has no opinion, and the transport falls back to {@code spring.mail.*}
 * - so a deployment configured the properties way is not silently overridden by half a screen of
 * settings, and a deployment configured here is not disturbed by a stale property file.</p>
 */
@Entity
@Table(name = "email_settings")
public class EmailSetting extends AuditableEntity implements Persistable<Long> {

    public static final long SINGLETON_ID = 1L;

    @jakarta.persistence.Transient private boolean attached;

    @Id private Long id = SINGLETON_ID;

    @Column(name = "smtp_host", length = 255) private String smtpHost;
    @Column(name = "smtp_port") private Integer smtpPort;
    @Column(name = "smtp_username", length = 190) private String smtpUsername;
    /** The sealed value. Nothing returns it, and a save that does not carry one leaves it in place. */
    @Column(name = "smtp_secret", length = 1000) private String smtpSecret;
    @Column(name = "smtp_secret_set_at") private Instant secretSetAt;
    @Column(name = "auth_enabled", nullable = false) private boolean authEnabled = true;

    @Enumerated(EnumType.STRING) @Column(name = "security_protocol", nullable = false, length = 16)
    private EmailSecurityProtocol securityProtocol = EmailSecurityProtocol.NONE;

    @Column(name = "timeout_ms", nullable = false) private int timeoutMs = 5000;
    @Column(name = "sender_name", length = 150) private String senderName;
    @Column(name = "sender_email", length = 190) private String senderEmail;
    @Column(name = "reply_to_email", length = 190) private String replyToEmail;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private ContentStatus status = ContentStatus.INACTIVE;

    @Override public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    @Override public boolean isNew() { return !attached; }

    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String v) { this.smtpHost = v; }
    public Integer getSmtpPort() { return smtpPort; }
    public void setSmtpPort(Integer v) { this.smtpPort = v; }
    public String getSmtpUsername() { return smtpUsername; }
    public void setSmtpUsername(String v) { this.smtpUsername = v; }
    public String getSmtpSecret() { return smtpSecret; }
    public void setSmtpSecret(String v) { this.smtpSecret = v; }
    public Instant getSecretSetAt() { return secretSetAt; }
    public void markSecretSet(Instant when) { this.secretSetAt = when; }
    public boolean isAuthEnabled() { return authEnabled; }
    public void setAuthEnabled(boolean v) { this.authEnabled = v; }
    public EmailSecurityProtocol getSecurityProtocol() { return securityProtocol; }
    public void setSecurityProtocol(EmailSecurityProtocol v) { this.securityProtocol = v; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int v) { this.timeoutMs = v; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String v) { this.senderName = v; }
    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String v) { this.senderEmail = v; }
    public String getReplyToEmail() { return replyToEmail; }
    public void setReplyToEmail(String v) { this.replyToEmail = v; }
    public ContentStatus getStatus() { return status; }
    public void setStatus(ContentStatus v) { this.status = v; }

    /** True when an administrator has actually pointed this installation at a mail server. */
    public boolean isUsable() { return smtpHost != null && !smtpHost.isBlank(); }
    public boolean hasCredential() { return smtpSecret != null && !smtpSecret.isBlank(); }

    public static EmailSetting attached(EmailSetting row) { row.attached = true; return row; }
    public static EmailSetting defaults() {
        EmailSetting row = new EmailSetting();
        row.id = SINGLETON_ID;
        return row;
    }
}
