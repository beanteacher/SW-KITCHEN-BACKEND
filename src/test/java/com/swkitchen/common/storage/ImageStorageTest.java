package com.swkitchen.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.swkitchen.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@SpringBootTest
@Import(TestcontainersConfig.class)
class ImageStorageTest {

    @Autowired
    ImageStorage imageStorage;

    @Autowired
    S3Client s3Client;

    @Test
    @DisplayName("무작위 이름으로 올리고, 같은 내용과 형식으로 다시 받을 수 있다")
    void upload() {
        byte[] content = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

        String key = imageStorage.upload(content, "jpg", "image/jpeg");

        assertThat(key).matches("products/[0-9a-f-]{36}\\.jpg");
        ResponseBytes<GetObjectResponse> saved = s3Client.getObjectAsBytes(request -> request
            .bucket("sw-kitchen")
            .key(key));
        assertThat(saved.asByteArray()).isEqualTo(content);
        assertThat(saved.response().contentType()).isEqualTo("image/jpeg");
    }
}
