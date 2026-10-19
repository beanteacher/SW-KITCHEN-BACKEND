package com.swkitchen.common.storage;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** 제품 이미지를 S3 에 올린다. 원래 파일 이름은 쓰지 않고 서버가 만든 무작위 이름으로 저장한다 */
@Component
@RequiredArgsConstructor
public class ImageStorage {

    private final S3Client s3Client;
    private final StorageProperties properties;

    /** 올린 파일의 키(예: products/{UUID}.jpg)를 돌려준다 */
    public String upload(byte[] content, String extension, String contentType) {
        String key = "products/" + UUID.randomUUID() + "." + extension;
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key)
            .contentType(contentType)
            .build();
        s3Client.putObject(request, RequestBody.fromBytes(content));
        return key;
    }
}
