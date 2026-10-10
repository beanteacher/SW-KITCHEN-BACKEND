package com.swkitchen;

import com.swkitchen.common.config.QuerydslConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Repository 테스트 설정 묶음. 실제 MySQL 컨테이너로 돌린다.
 * QueryDSL 구현체가 있는 Repository 도 함께 만들어지므로 JPAQueryFactory 설정을 같이 읽는다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfig.class, QuerydslConfig.class})
public @interface RepositoryTest {
}
