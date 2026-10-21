package com.swkitchen.common.storage;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
public class StorageConfig {

    @Bean
    public S3Client s3Client(StorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder().region(Region.of(properties.region()));
        if (StringUtils.hasText(properties.endpoint())) {
            // LocalStack 은 아무 키나 받는다. 주소에 버킷 이름을 붙이는 방식(sw-kitchen.localhost)을 못 써서 경로 방식으로 붙는다
            builder.endpointOverride(URI.create(properties.endpoint()))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
        }
        // 운영은 서버에 걸린 AWS 권한(환경 변수·IAM 역할)을 SDK 가 알아서 찾는다
        return builder.build();
    }
}
