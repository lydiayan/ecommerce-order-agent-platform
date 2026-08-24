package com.css.mallorderagent.feedback;

import com.css.mallorderagent.config.FeedbackProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeedbackCryptoTest {

    @Test
    void encrypt_usesRandomIvAndRoundTrips() {
        FeedbackCrypto crypto = crypto("0123456789abcdef-feedback-key");

        String first = crypto.encrypt("订单回答快照");
        String second = crypto.encrypt("订单回答快照");

        assertNotEquals(first, second);
        assertEquals("订单回答快照", crypto.decrypt(first));
        assertEquals("订单回答快照", crypto.decrypt(second));
    }

    @Test
    void decrypt_withDifferentKeyFailsAuthentication() {
        String encrypted = crypto("0123456789abcdef-feedback-key").encrypt("sensitive");

        assertThrows(IllegalStateException.class,
                () -> crypto("different-key-material-123456").decrypt(encrypted));
    }

    private static FeedbackCrypto crypto(String keyMaterial) {
        FeedbackProperties properties = new FeedbackProperties();
        properties.setEncryptionKey(keyMaterial);
        FeedbackCrypto crypto = new FeedbackCrypto(properties);
        crypto.initialize();
        return crypto;
    }
}
