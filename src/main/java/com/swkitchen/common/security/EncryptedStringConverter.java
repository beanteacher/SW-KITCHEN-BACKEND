package com.swkitchen.common.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 서버 코드는 원래 값을 쓰고 DB 에만 암호문이 있다 */
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final PrivacyCrypto privacyCrypto;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : privacyCrypto.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : privacyCrypto.decrypt(dbData);
    }
}
