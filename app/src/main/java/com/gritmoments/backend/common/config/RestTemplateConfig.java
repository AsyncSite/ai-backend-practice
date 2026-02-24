package com.gritmoments.backend.common.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 설정 (세션 03: 외부 API 연동)
 *
 * 외부 PG API 호출에 사용되는 RestTemplate을 Bean으로 등록합니다.
 * - 연결 타임아웃: 3초
 * - 읽기 타임아웃: 5초
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
