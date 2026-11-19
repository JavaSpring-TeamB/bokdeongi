package com.bokdeongi.backend.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 민감정보 암호화 Converter.
 *
 * 민감정보 코드 목록을 JSON 문자열로 전달받아
 * AES-256-GCM 방식으로 암호화한 후 DB에 저장한다.
 *
 * DB 조회 시에는 암호문을 복호화하여 JSON 문자열로 반환한다.
 *
 * null은 민감정보 미입력 또는 건너뛰기를 의미하며,
 * 빈 목록("[]")은 '해당 없음'을 명시적으로 선택한 상태를 의미한다.
 */
@Converter(autoApply = false)
public class EncryptedSensitiveConverter
        implements AttributeConverter<String, byte[]> {

    // AES-256-GCM 암호화 및 복호화를 담당하는 서비스
    private final FieldEncryptionService fieldEncryptionService;

    /**
     * 암호화 서비스를 주입받는다.
     */
    public EncryptedSensitiveConverter(
            FieldEncryptionService fieldEncryptionService
    ) {
        this.fieldEncryptionService = fieldEncryptionService;
    }

    /**
     * 민감정보 JSON 문자열을 암호화하여 DB에 저장한다.
     *
     * null인 경우 암호화하지 않고 DB에 NULL을 저장한다.
     * 빈 목록("[]")도 실제 입력값이므로 암호화하여 저장한다.
     *
     * @param sensitiveCodesJson 민감정보 코드 목록의 JSON 문자열
     * @return AES-256-GCM으로 암호화된 바이트 배열
     */
    @Override
    public byte[] convertToDatabaseColumn(String sensitiveCodesJson) {
        if (sensitiveCodesJson == null) {
            return null;
        }

        return fieldEncryptionService.encrypt(sensitiveCodesJson);
    }

    /**
     * DB에 저장된 민감정보 암호문을 복호화한다.
     *
     * DB 값이 NULL이면 null을 반환하며,
     * 암호화된 데이터가 존재하면 원본 JSON 문자열로 복원한다.
     *
     * @param encrypted DB에 저장된 민감정보 암호문
     * @return 복호화된 민감정보 JSON 문자열
     */
    @Override
    public String convertToEntityAttribute(byte[] encrypted) {
        if (encrypted == null) {
            return null;
        }

        return fieldEncryptionService.decrypt(encrypted);
    }
}