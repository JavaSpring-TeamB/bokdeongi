package com.bokdeongi.backend.common.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class FieldEncryptionServiceTest {

    private FieldEncryptionService fieldEncryptionService;

    @BeforeEach
    void setUp() {
        // 테스트 전용 AES-256 암호화 키 생성
        // 실제 운영 환경의 .env 키는 사용하지 않는다.
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);

        String encodedKey = Base64.getEncoder()
                .encodeToString(keyBytes);

        // 실제 암호화 서비스를 생성한다.
        fieldEncryptionService = new FieldEncryptionService(encodedKey);
    }

    /**
     * 원본 문자열을 암호화한 뒤 복호화하면
     * 원래 문자열로 복원되어야 한다.
     */
    @Test
    void shouldEncryptAndDecryptSensitiveInfo() {
        // Given
        String original = "[\"JA0301\"]";

        // When
        byte[] encrypted = fieldEncryptionService.encrypt(original);
        String decrypted = fieldEncryptionService.decrypt(encrypted);

        // Then
        assertNotNull(encrypted);
        assertEquals(original, decrypted);

        // 암호화된 데이터는 평문과 달라야 한다.
        assertFalse(
                java.util.Arrays.equals(
                        original.getBytes(StandardCharsets.UTF_8),
                        encrypted
                )
        );
    }

    /**
     * 동일한 문자열을 두 번 암호화해도
     * 랜덤 IV 때문에 서로 다른 암호문이 생성되어야 한다.
     */
    @Test
    void shouldGenerateDifferentCiphertexts() {
        // Given
        String original = "[\"JA0301\"]";

        // When
        byte[] encrypted1 = fieldEncryptionService.encrypt(original);
        byte[] encrypted2 = fieldEncryptionService.encrypt(original);

        // Then
        assertFalse(
                java.util.Arrays.equals(encrypted1, encrypted2)
        );

        // 두 암호문 모두 정상적으로 복호화되어야 한다.
        assertEquals(original, fieldEncryptionService.decrypt(encrypted1));
        assertEquals(original, fieldEncryptionService.decrypt(encrypted2));
    }

    /**
     * 암호문이 변조되면
     * AES-GCM 인증 태그 검증에 실패해야 한다.
     */
    @Test
    void shouldRejectTamperedCiphertext() {
        // Given
        byte[] encrypted = fieldEncryptionService.encrypt(
                "[\"JA0301\"]"
        );

        // 암호문의 마지막 바이트를 변조한다.
        encrypted[encrypted.length - 1] ^= 1;

        // When & Then
        assertThrows(
                IllegalStateException.class,
                () -> fieldEncryptionService.decrypt(encrypted)
        );
    }

    /**
     * null을 암호화하거나 복호화하면
     * 그대로 null을 반환해야 한다.
     */
    @Test
    void shouldHandleNullValues() {
        assertNull(fieldEncryptionService.encrypt(null));
        assertNull(fieldEncryptionService.decrypt(null));
    }

    /**
     * 빈 목록("[]")도 실제 입력값이므로
     * 정상적으로 암호화 및 복호화되어야 한다.
     */
    @Test
    void shouldEncryptAndDecryptEmptyList() {
        // Given
        String original = "[]";

        // When
        byte[] encrypted = fieldEncryptionService.encrypt(original);
        String decrypted = fieldEncryptionService.decrypt(encrypted);

        // Then
        assertNotNull(encrypted);
        assertEquals(original, decrypted);
    }

    /**
     * 암호화 키가 32바이트가 아니면
     * 서비스를 생성할 수 없어야 한다.
     */
    @Test
    void shouldRejectInvalidKeyLength() {
        // Given: 잘못된 16바이트 키
        byte[] invalidKey = new byte[16];

        String encodedKey = Base64.getEncoder()
                .encodeToString(invalidKey);

        // When & Then
        assertThrows(
                IllegalStateException.class,
                () -> new FieldEncryptionService(encodedKey)
        );
    }

    /**
     * Base64 형식이 아닌 암호화 키는
     * 거부되어야 한다.
     */
    @Test
    void shouldRejectInvalidBase64Key() {
        assertThrows(
                IllegalStateException.class,
                () -> new FieldEncryptionService("invalid-key!")
        );
    }

    /**
     * 암호화 데이터가 최소 길이보다 짧으면
     * 복호화를 거부해야 한다.
     */
    @Test
    void shouldRejectTooShortEncryptedData() {
        // Given
        byte[] invalidData = {1, 2, 3};

        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () -> fieldEncryptionService.decrypt(invalidData)
        );
    }
}