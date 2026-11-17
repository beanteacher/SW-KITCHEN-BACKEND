package com.swkitchen.common.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@Converter
@RequiredArgsConstructor
public class EncryptedLocalDateConverter implements AttributeConverter<LocalDate, String> {

    private final PrivacyCrypto privacyCrypto;

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : privacyCrypto.encrypt(attribute.toString());
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LocalDate.parse(privacyCrypto.decrypt(dbData));
    }
}
