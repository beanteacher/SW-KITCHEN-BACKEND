package com.swkitchen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class SwKitchenApplicationTest {

    @Test
    @DisplayName("실제 MySQL 에 붙어 Flyway·JPA 검증까지 마치고 앱이 뜬다")
    void contextLoads() {
    }
}
