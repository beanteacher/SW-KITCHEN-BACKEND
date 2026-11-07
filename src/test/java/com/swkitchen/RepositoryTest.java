package com.swkitchen;

import com.swkitchen.common.config.QuerydslConfig;
import com.swkitchen.common.security.EncryptedLocalDateConverter;
import com.swkitchen.common.security.EncryptedStringConverter;
import com.swkitchen.common.security.EncryptionProperties;
import com.swkitchen.common.security.PrivacyCrypto;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Repository 테스트 설정 묶음. 실제 MySQL 컨테이너로 돌린다.
 * QueryDSL 구현체가 있는 Repository 도 함께 만들어지므로 JPAQueryFactory 설정을 같이 읽는다.
 * 개인정보 칸의 암호화 변환기도 Spring Bean 이라 같이 읽는다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    TestcontainersConfig.class,
    QuerydslConfig.class,
    PrivacyCrypto.class,
    EncryptedStringConverter.class,
    EncryptedLocalDateConverter.class})
@EnableConfigurationProperties(EncryptionProperties.class)
public @interface RepositoryTest {
}
