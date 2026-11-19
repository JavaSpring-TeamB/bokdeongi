package com.bokdeongi.backend.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 소득 금액 암호화 Converter.
 * BigDecimal 금액을 AES-GCM 암호문으로 변환해 DB에 저장한다.
 */
@Converter(autoApply = false)
public class EncryptedIncomeConverter
        implements AttributeConverter<java.math.BigDecimal, byte[]> {

    private final FieldEncryptionService fieldEncryptionService;

    public EncryptedIncomeConverter(
            FieldEncryptionService fieldEncryptionService
    ) {
        this.fieldEncryptionService = fieldEncryptionService;
    }

    /** 소득 금액을 암호화해 저장한다. */
    @Override
    public byte[] convertToDatabaseColumn(java.math.BigDecimal amount) {
        if (amount == null) {
            return null;
        }

        return fieldEncryptionService.encrypt(amount.toPlainString());
    }

    /** 암호화된 소득 금액을 복호화한다. */
    @Override
    public java.math.BigDecimal convertToEntityAttribute(byte[] encrypted) {
        if (encrypted == null) {
            return null;
        }

        return new java.math.BigDecimal(
                fieldEncryptionService.decrypt(encrypted)
        );
    }
}