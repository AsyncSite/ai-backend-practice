package com.gritmoments.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;

/**
 * AI와 함께 배우는 백엔드 실전 가이드 - 메인 애플리케이션
 *
 * 이커머스(음식 주문) 서비스의 백엔드 서버입니다.
 * 13개 세션에 걸쳐 기능을 하나씩 추가하면서 백엔드 핵심 역량을 학습합니다.
 *
 * - @EnableCaching: 세션 01 (Redis 캐시) 활성화
 * - @EnableRetry: 세션 03 (Spring Retry) 활성화
 */
@SpringBootApplication
@EnableCaching
@EnableRetry
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
