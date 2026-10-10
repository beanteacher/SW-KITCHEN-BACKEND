package com.swkitchen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// 로그인은 직접 만든 /api/v1/auth/login 으로만 한다. Spring 기본 임시 계정은 만들지 않는다
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class SwKitchenApplication {

    public static void main(String[] args) {
        SpringApplication.run(SwKitchenApplication.class, args);
    }
}
