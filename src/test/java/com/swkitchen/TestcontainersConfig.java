package com.swkitchen;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** DB·S3 가 필요한 테스트가 함께 쓰는 MySQL·LocalStack 컨테이너 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysql() {
        return new MySQLContainer<>("mysql:8.4");
    }

    /** 로컬 docker compose 와 같은 스크립트로 버킷을 만든다. 버킷을 다 만든 뒤에 준비 완료가 된다 */
    @Bean
    LocalStackContainer localStack() {
        return new LocalStackContainer(DockerImageName.parse("localstack/localstack:4"))
            .withServices(Service.S3)
            .withCopyFileToContainer(
                MountableFile.forHostPath("docker/localstack/create-bucket.sh", 0755),
                "/etc/localstack/init/ready.d/create-bucket.sh");
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(LocalStackContainer localStack) {
        return registry -> registry.add("storage.endpoint", () -> localStack.getEndpoint().toString());
    }
}
