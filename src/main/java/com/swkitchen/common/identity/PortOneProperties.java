package com.swkitchen.common.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("portone")
public record PortOneProperties(String apiSecret) {
}
