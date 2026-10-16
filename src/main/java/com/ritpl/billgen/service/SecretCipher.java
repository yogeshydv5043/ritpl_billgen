package com.ritpl.billgen.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Encrypts the one credential an administrator types - the SMTP password - so the database holds an
 * unreadable envelope instead of a password. AES/GCM with a fresh random IV per seal, and the
 * ciphertext is versioned ({@code v1:} prefix) so a future key rotation can tell old from new.
 *
 * <p>The key comes from {@code app.secrets.encryption-key} and nothing else: it is not derived from a
 * constant, a URL or the data it protects. Without a key the application still runs, but a credential
 * cannot be stored - so {@link #seal(String)} refuses rather than falling back to plain text, which is
 * the failure mode that would look like success.</p>
 */
@Service
public class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final boolean available;

    public SecretCipher(@Value("${app.secrets.encryption-key:}") String configuredKey) {
        SecretKeySpec derived = null;
        if (configuredKey != null && !configuredKey.isBlank()) {
            try {
                // Any secret is accepted; SHA-256 folds it to a key of the right length, so an operator
                // can paste a passphrase rather than generate 32 bytes of base64 by hand.
                byte[] material = MessageDigest.getInstance("SHA-256")
                        .digest(configuredKey.trim().getBytes(StandardCharsets.UTF_8));
                derived = new SecretKeySpec(material, 0, KEY_BYTES, "AES");
            } catch (Exception unusable) {
                derived = null;
            }
        }
        this.key = derived;
        this.available = derived != null;
    }

    /** True when a credential can be stored at all. The settings screen says so on the page. */
    public boolean isAvailable() { return available; }

    public String seal(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return null;
        if (!available) {
            throw new IllegalArgumentException("Set app.secrets.encryption-key before storing a mail password. "
                    + "Until then, leave the password field empty and keep the transport in configuration.");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            new java.security.SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(sealed, 0, packed, iv.length, sealed.length);
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (Exception failed) {
            throw new IllegalStateException("The mail password could not be encrypted.", failed);
        }
    }

    /** The plaintext, or null when nothing was stored or it was sealed with a key this instance lacks. */
    public String open(String sealed) {
        if (sealed == null || !sealed.startsWith(PREFIX) || !available) return null;
        try {
            byte[] packed = Base64.getDecoder().decode(sealed.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(packed, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(packed, IV_BYTES, packed.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (RuntimeException | java.security.GeneralSecurityException unreadable) {
            // Deliberately silent about why: a wrong key and a corrupt value both mean the same to the
            // caller, and neither is a reason to echo decryption detail into a log.
            return null;
        }
    }
}
