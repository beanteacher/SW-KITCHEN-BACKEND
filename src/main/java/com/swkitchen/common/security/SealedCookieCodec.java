package com.swkitchen.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 쿠키에 담는 값을 JSON 으로 바꿔 암호화한다. 화면은 읽지도 고치지도 못한다 */
@Component
public class SealedCookieCodec {

    private final AesGcm aesGcm;
    private final ObjectMapper objectMapper;

    public SealedCookieCodec(EncryptionProperties properties, ObjectMapper objectMapper) {
        this.aesGcm = new AesGcm(properties.cookieKey());
        this.objectMapper = objectMapper;
    }

    public String seal(Object value) {
        try {
            return aesGcm.encrypt(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 없거나 고쳐졌거나 모양이 다르면 비어 있다 */
    public <T> Optional<T> open(String sealed, Class<T> type) {
        if (!StringUtils.hasText(sealed)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(aesGcm.decrypt(sealed), type));
        } catch (IllegalArgumentException | JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
