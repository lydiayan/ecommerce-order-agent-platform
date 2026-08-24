package com.css.mallorderagent.feedback;

import com.css.mallorderagent.config.FeedbackProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class FeedbackCrypto {

    private static final Logger log = LoggerFactory.getLogger(FeedbackCrypto.class);
    private static final String LOCAL_DEFAULT_KEY = "local-feedback-key-change-before-production";
    private static final byte FORMAT_VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final FeedbackProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKeySpec key;

    public FeedbackCrypto(FeedbackProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        String keyMaterial = properties.getEncryptionKey();
        if (keyMaterial == null || keyMaterial.length() < 16) {
            throw new IllegalStateException("agent.feedback.encryption-key must contain at least 16 characters");
        }
        if (LOCAL_DEFAULT_KEY.equals(keyMaterial)) {
            log.warn("Agent feedback uses the local default encryption key; configure "
                    + "AGENT_FEEDBACK_ENCRYPTION_KEY before production");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
            key = new SecretKeySpec(digest, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        ensureInitialized();
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer payload = ByteBuffer.allocate(1 + iv.length + encrypted.length);
            payload.put(FORMAT_VERSION).put(iv).put(encrypted);
            return Base64.getEncoder().encodeToString(payload.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt feedback content", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        ensureInitialized();
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext);
            if (payload.length <= 1 + IV_LENGTH || payload[0] != FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported feedback ciphertext format");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - 1 - IV_LENGTH];
            System.arraycopy(payload, 1, iv, 0, IV_LENGTH);
            System.arraycopy(payload, 1 + IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Unable to decrypt feedback content", e);
        }
    }

    private void ensureInitialized() {
        if (key == null) initialize();
    }
}
