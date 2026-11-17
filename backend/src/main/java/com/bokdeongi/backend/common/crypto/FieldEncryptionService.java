package com.bokdeongi.backend.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class FieldEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public FieldEncryptionService(
            @Value("${app.encryption.key}") String encodedKey
    ) {
        byte[] keyBytes;

        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "암호화 키는 Base64 형식이어야 합니다.", e
            );
        }

        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "AES-256 암호화 키는 32바이트여야 합니다."
            );
        }

        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 문자열을 AES-256-GCM으로 암호화한다.
     *
     * 저장 형식: IV(12바이트) + 암호문 + 인증 태그
     */
    public byte[] encrypt(String plaintext) {

        if (plaintext == null) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(TAG_LENGTH, iv)
            );

            byte[] encrypted = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8)
            );

            return ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "민감정보 암호화에 실패했습니다.", e
            );
        }
    }

    /**
     * AES-256-GCM 암호문을 문자열로 복호화한다.
     */
    public String decrypt(byte[] encryptedData) {

        if (encryptedData == null) {
            return null;
        }

        if (encryptedData.length < IV_LENGTH + 16) {
            throw new IllegalArgumentException(
                    "암호화된 데이터 형식이 올바르지 않습니다."
            );
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(encryptedData);

            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(TAG_LENGTH, iv)
            );

            byte[] decrypted = cipher.doFinal(ciphertext);

            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "민감정보 복호화에 실패했습니다.", e
            );
        }
    }
}