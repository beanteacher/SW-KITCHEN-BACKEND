package com.swkitchen.common.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

// fillSimulatorBlanks: KCP 테스트(시뮬레이터)는 휴대폰 번호·성별을 비워서 준다. 로컬에서만 켜서 테스트 값으로 채운다
@ConfigurationProperties("portone")
public record PortOneProperties(String apiSecret, boolean fillSimulatorBlanks) {
}
