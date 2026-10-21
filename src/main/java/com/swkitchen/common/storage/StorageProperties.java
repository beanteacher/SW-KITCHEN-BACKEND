package com.swkitchen.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** endpoint 는 로컬·테스트의 LocalStack 주소다. 운영(AWS S3)은 비워 둔다 */
@ConfigurationProperties("storage")
public record StorageProperties(String bucket, String region, String endpoint) {
}
