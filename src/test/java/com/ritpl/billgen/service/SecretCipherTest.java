package com.ritpl.billgen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The mail password is the one credential this application stores as data, so the tests are about the
 * three ways that can go wrong: the envelope containing the password in plain text, a deployment without
 * a key quietly accepting and losing it, and a reader who guesses a key and gets a plaintext back.
 */
class SecretCipherTest {

    private static final String PASSWORD = "smtp-s3cret-p4ss";

    @Test
    void sealsAndOpensRoundTrip() {
        SecretCipher cipher = new SecretCipher("a-test-key-of-some-length");
        String sealed = cipher.seal(PASSWORD);
        assertNotNull(sealed);
        assertFalse(sealed.contains(PASSWORD), "the envelope must not contain the password");
        assertTrue(sealed.startsWith("v1:"));
        assertEquals(PASSWORD, cipher.open(sealed));
    }

    @Test
    void everySealIsDifferentSoTheSamePasswordCannotBeRecognised() {
        SecretCipher cipher = new SecretCipher("a-test-key-of-some-length");
        assertFalse(cipher.seal(PASSWORD).equals(cipher.seal(PASSWORD)));
    }

    @Test
    void refusesToStoreWhenNoKeyIsConfigured() {
        SecretCipher cipher = new SecretCipher("");
        assertFalse(cipher.isAvailable());
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> cipher.seal(PASSWORD));
        assertTrue(refused.getMessage().contains("app.secrets.encryption-key"),
                "the message says what to do rather than failing silently");
    }

    @Test
    void anotherKeyCannotReadItAndALuckyGuesserGetsNothing() {
        String sealed = new SecretCipher("correct-key").seal(PASSWORD);
        assertNull(new SecretCipher("wrong-key").open(sealed));
        assertNull(new SecretCipher("correct-key").open("not-an-envelope"));
        assertNull(new SecretCipher("correct-key").open(null));
        assertNull(new SecretCipher("correct-key").open("v1:!!!"));
    }

    @Test
    void emptyAndNullAreNotThingsToEncrypt() {
        SecretCipher cipher = new SecretCipher("correct-key");
        assertNull(cipher.seal(null));
        assertNull(cipher.seal(""));
    }
}
