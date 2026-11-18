package com.bokdeongi.backend.common.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EncryptedSensitiveConverterTest {

    private FieldEncryptionService fieldEncryptionService;
    private EncryptedSensitiveConverter converter;

    @BeforeEach
    void setUp() {
        // 실제 암호화 서비스 대신 Mock 객체를 사용한다.
        fieldEncryptionService = mock(FieldEncryptionService.class);

        // 테스트할 JPA Converter 생성
        converter = new EncryptedSensitiveConverter(
                fieldEncryptionService
        );
    }

    /**
     * 민감정보를 입력하지 않은 경우
     * DB에도 NULL이 저장되어야 한다.
     */
    @Test
    void shouldKeepNullWhenEncrypting() {
        // When
        byte[] result = converter.convertToDatabaseColumn(null);

        // Then
        assertNull(result);

        // NULL은 암호화 서비스로 전달하지 않는다.
        verifyNoInteractions(fieldEncryptionService);
    }

    /**
     * '해당 없음'을 선택한 경우
     * 빈 목록도 암호화 대상이어야 한다.
     */
    @Test
    void shouldEncryptEmptySensitiveCodes() {
        // Given
        String sensitiveCodesJson = "[]";
        byte[] encryptedData = {1, 2, 3};

        when(fieldEncryptionService.encrypt(sensitiveCodesJson))
                .thenReturn(encryptedData);

        // When
        byte[] result = converter.convertToDatabaseColumn(
                sensitiveCodesJson
        );

        // Then
        assertArrayEquals(encryptedData, result);

        verify(fieldEncryptionService).encrypt("[]");
    }

    /**
     * 선택한 민감정보 코드가 암호화 서비스로
     * 정상적으로 전달되는지 확인한다.
     */
    @Test
    void shouldEncryptSelectedSensitiveCodes() {
        // Given
        String sensitiveCodesJson = "[\"JA0301\"]";
        byte[] encryptedData = {4, 5, 6};

        when(fieldEncryptionService.encrypt(sensitiveCodesJson))
                .thenReturn(encryptedData);

        // When
        byte[] result = converter.convertToDatabaseColumn(
                sensitiveCodesJson
        );

        // Then
        assertArrayEquals(encryptedData, result);

        verify(fieldEncryptionService)
                .encrypt("[\"JA0301\"]");
    }

    /**
     * DB에서 NULL을 읽은 경우
     * 복호화 없이 null을 반환해야 한다.
     */
    @Test
    void shouldKeepNullWhenDecrypting() {
        // When
        String result = converter.convertToEntityAttribute(null);

        // Then
        assertNull(result);

        verifyNoInteractions(fieldEncryptionService);
    }

    /**
     * 암호화된 민감정보를 읽으면
     * 원래 JSON 문자열로 복원해야 한다.
     */
    @Test
    void shouldDecryptSensitiveCodes() {
        // Given
        byte[] encryptedData = {1, 2, 3};
        String originalJson = "[\"JA0301\"]";

        when(fieldEncryptionService.decrypt(encryptedData))
                .thenReturn(originalJson);

        // When
        String result = converter.convertToEntityAttribute(
                encryptedData
        );

        // Then
        assertEquals(originalJson, result);

        verify(fieldEncryptionService).decrypt(encryptedData);
    }
}