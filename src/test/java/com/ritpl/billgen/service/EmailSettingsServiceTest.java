package com.ritpl.billgen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.EmailSecurityProtocol;
import com.ritpl.billgen.entity.EmailSetting;
import com.ritpl.billgen.repository.EmailSettingRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * The rules that make a stored SMTP password safe live in this service rather than in the database, so
 * they are tested here without a mail server: an empty submission must not clear the credential, an
 * incomplete transport must not be activated, and no read path may hand the password back. The sealing
 * itself is {@link SecretCipherTest}'s business.
 */
class EmailSettingsServiceTest {

    private static final String PASSWORD = "smtp-s3cret-p4ss";
    private static final TenantScope ADMIN = TenantScope.administrator();

    private final EmailSettingRepository store = mock(EmailSettingRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final EmailSetting row = EmailSetting.attached(completeRow());

    @Test
    void anEmptyPasswordFieldKeepsTheStoredCredential() {
        EmailSetting saved = save(new EmailSettingsCommand("smtp.example.test", 465, "relay@ritpayments.test",
                "   ", true, EmailSecurityProtocol.SSL_TLS, 8000, "RIT Payments", "billing@ritpayments.test",
                null, ContentStatus.ACTIVE));
        assertEquals("v1:kept-from-before", saved.getSmtpSecret(), "whitespace is not a new password");
        assertEquals(465, saved.getSmtpPort().intValue());
        assertEquals(EmailSecurityProtocol.SSL_TLS, saved.getSecurityProtocol(), "the rest of the form did change");
    }

    @Test
    void aNewPasswordIsSealedBeforeItReachesTheColumn() {
        EmailSetting saved = save(new EmailSettingsCommand("smtp.example.test", 587, "relay@ritpayments.test",
                PASSWORD, true, EmailSecurityProtocol.START_TLS, 8000, "RIT Payments",
                "billing@ritpayments.test", null, ContentStatus.ACTIVE));
        assertTrue(saved.getSmtpSecret().startsWith("v1:"));
        assertFalse(saved.getSmtpSecret().contains(PASSWORD));
        assertNotNull(saved.getSecretSetAt(), "the row records when the credential was replaced");
    }

    @Test
    void anIncompleteTransportCannotBeActivatedAndSaysWhatIsMissing() {
        when(store.findById(anyLong())).thenReturn(Optional.empty());
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> service(new SecretCipher("test-key")).save(ADMIN, new EmailSettingsCommand(null, null, null,
                        null, false, EmailSecurityProtocol.NONE, null, "RIT Payments", null, null,
                        ContentStatus.ACTIVE)));
        assertTrue(refused.getMessage().contains("from address"), refused.getMessage());
        assertTrue(refused.getMessage().contains("host"), refused.getMessage());
    }

    @Test
    void aMissingEncryptionKeyIsRefusedRatherThanStoredInPlainText() {
        EmailSettingsService withoutKey = service(new SecretCipher(""));
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> withoutKey.save(ADMIN, new EmailSettingsCommand("smtp.example.test", 587, "relay@x.test",
                        PASSWORD, true, EmailSecurityProtocol.START_TLS, 8000, "RIT", "billing@x.test", null,
                        ContentStatus.INACTIVE)));
        assertTrue(refused.getMessage().contains("app.secrets.encryption-key"), refused.getMessage());
        assertFalse(withoutKey.view().credentialStorageAvailable(), "and the screen can say so before anybody types");
        assertEquals("v1:kept-from-before", row.getSmtpSecret(),
                "the refused attempt wrote nothing, so the old sealed value is untouched");
    }

    @Test
    void theReadModelCarriesTheFactsAboutACredentialButNeverTheCredential() {
        save(new EmailSettingsCommand("smtp.example.test", 587, "relay@ritpayments.test", PASSWORD, true,
                EmailSecurityProtocol.START_TLS, 8000, "RIT Payments", "billing@ritpayments.test", null,
                ContentStatus.ACTIVE));
        EmailSettingsService.View view = service(new SecretCipher("test-key")).view();
        assertTrue(view.passwordSet());
        assertTrue(view.transportLive());
        assertFalse(view.toString().contains(PASSWORD), "a debug print of the view must be safe to paste");
        assertEquals("SMTP smtp.example.test:587 (StartTLS, from billing@ritpayments.test)",
                view.transportSummary());
    }

    @Test
    void aTestSendIsRefusedWithAnExplanationWhileTheTransportIsInactive() {
        EmailSetting inactive = completeRow();
        inactive.setStatus(ContentStatus.INACTIVE);
        when(store.findById(anyLong())).thenReturn(Optional.of(EmailSetting.attached(inactive)));
        EmailSettingsService svc = new EmailSettingsService(store, new SecretCipher("test-key"), audit);
        EmailSettingsService.TestResult result = svc.test(ADMIN, "someone@example.test");
        assertFalse(result.sent());
        assertTrue(result.message().contains("not active and complete"), result.message());
    }

    @Test
    void nonsenseInputIsRefusedBeforeAnythingIsStored() {
        assertThrows(IllegalArgumentException.class, () -> save(new EmailSettingsCommand("https://smtp.test",
                587, "u", "p", true, EmailSecurityProtocol.START_TLS, 8000, "n", "a@b.test", null,
                ContentStatus.INACTIVE)), "a URL is not a server name");
        assertThrows(IllegalArgumentException.class, () -> save(new EmailSettingsCommand("smtp.test", 587, null,
                "p", true, EmailSecurityProtocol.START_TLS, 8000, "n", "a@b.test", null, ContentStatus.INACTIVE)),
                "authentication needs a username");
        assertThrows(IllegalArgumentException.class, () -> save(new EmailSettingsCommand("smtp.test", 587, "u",
                "p", true, EmailSecurityProtocol.START_TLS, 5, "n", "a@b.test", null, ContentStatus.INACTIVE)),
                "a five millisecond timeout would fail every send");
        assertThrows(IllegalArgumentException.class, () -> save(new EmailSettingsCommand("smtp.test", 587, "u",
                "p", false, EmailSecurityProtocol.SSL_TLS, 8000, "n", "a@b.test", null, ContentStatus.INACTIVE)),
                "an unauthenticated TLS connection is not a thing");
    }

    private EmailSetting save(EmailSettingsCommand command) {
        return service(new SecretCipher("test-key")).save(ADMIN, command);
    }

    private EmailSettingsService service(SecretCipher cipher) {
        when(store.findById(anyLong())).thenReturn(Optional.of(row));
        when(store.save(any(EmailSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return new EmailSettingsService(store, cipher, audit);
    }

    private static EmailSetting completeRow() {
        EmailSetting row = new EmailSetting();
        row.setId(EmailSetting.SINGLETON_ID);
        row.setSmtpHost("smtp.example.test");
        row.setSmtpPort(587);
        row.setSmtpUsername("relay@ritpayments.test");
        row.setSmtpSecret("v1:kept-from-before");
        row.setSenderName("RIT Payments");
        row.setSenderEmail("billing@ritpayments.test");
        row.setStatus(ContentStatus.ACTIVE);
        return row;
    }
}
