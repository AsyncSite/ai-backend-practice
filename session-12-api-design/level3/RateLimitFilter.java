package com.gritmoments.backend.common.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [Session 12 - Level 3] Bucket4j를 이용한 Rate Limiter 구현
 *
 * Token Bucket 알고리즘으로 API 요청 제한을 구현합니다.
 * IP 주소 기반으로 분당 요청 수를 제한합니다.
 *
 * TODO: 아래의 TODO를 채워서 Rate Limiter를 완성하세요.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    // IP별 Bucket을 저장하는 맵
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // TODO 1: Rate Limit 설정 (분당 60회)
    private static final int REQUESTS_PER_MINUTE = 60;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // TODO 2: 클라이언트 IP 주소 추출
        // 힌트: String ip = getClientIP(request);
        String ip = getClientIP(request);

        // TODO 3: IP별 Bucket 가져오기 (없으면 생성)
        // 힌트: Bucket bucket = resolveBucket(ip);
        Bucket bucket = resolveBucket(ip);

        // TODO 4: Token 소비 시도
        // 힌트: if (bucket.tryConsume(1)) { ... }
        if (bucket.tryConsume(1)) {
            // Token이 있으면 요청 허용
            filterChain.doFilter(request, response);
        } else {
            // TODO 5: Rate Limit 초과 시 429 응답
            // 힌트:
            //   response.setStatus(429);
            //   response.setContentType("application/json");
            //   response.getWriter().write("{\"error\": \"Too Many Requests\"}");

            log.warn("[Rate Limit] IP {} 요청 제한 초과", ip);
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Too Many Requests\", \"message\": \"분당 " + REQUESTS_PER_MINUTE + "회 제한\"}");
        }
    }

    /**
     * IP별 Bucket 생성 또는 조회
     */
    private Bucket resolveBucket(String ip) {
        // TODO 6: IP가 이미 있으면 기존 Bucket 반환, 없으면 새로 생성
        // 힌트: buckets.computeIfAbsent(ip, k -> createNewBucket())
        return buckets.computeIfAbsent(ip, k -> createNewBucket());
    }

    /**
     * 새로운 Bucket 생성
     */
    private Bucket createNewBucket() {
        // TODO 7: Bucket4j로 Token Bucket 생성
        // 힌트:
        //   Bandwidth limit = Bandwidth.classic(REQUESTS_PER_MINUTE, Refill.intervally(REQUESTS_PER_MINUTE, Duration.ofMinutes(1)));
        //   return Bucket.builder().addLimit(limit).build();

        Bandwidth limit = Bandwidth.classic(
            REQUESTS_PER_MINUTE,
            Refill.intervally(REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIP(HttpServletRequest request) {
        // TODO 8: 프록시 환경에서 실제 IP 추출
        // X-Forwarded-For 헤더 확인
        String ip = request.getHeader("X-Forwarded-For");
        
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }

        // X-Forwarded-For는 쉼표로 구분된 여러 IP가 올 수 있음
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }
}


// ============================================
// 💡 Token Bucket 알고리즘
// ============================================
//
// Bucket에는 최대 N개의 Token이 들어감
// - 매초/분 일정량의 Token이 보충됨 (Refill)
// - 요청이 올 때마다 1개의 Token 소비
// - Token이 없으면 요청 거부 (429)
//
// 장점:
// - Burst 트래픽 허용 (순간적으로 많은 요청)
// - 평균적으로 일정 속도 유지


// ============================================
// 💡 분산 환경에서 Rate Limiting (Redis)
// ============================================
//
// @Configuration
// public class RateLimitConfig {
//
//     @Bean
//     public ProxyManager<String> proxyManager(RedissonClient redisson) {
//         return new ProxyManager<>(Bucket4j.extension(Redisson.class)
//             .proxyManagerForMap(redisson.getMap("rate-limit-buckets")));
//     }
// }
//
// public class RedisRateLimitFilter extends OncePerRequestFilter {
//
//     private final ProxyManager<String> proxyManager;
//
//     private Bucket resolveBucket(String ip) {
//         BucketConfiguration config = BucketConfiguration.builder()
//             .addLimit(Bandwidth.classic(60, Refill.intervally(60, Duration.ofMinutes(1))))
//             .build();
//
//         return proxyManager.builder().build(ip, config);
//     }
// }


// ============================================
// 💡 고급 기능
// ============================================
//
// 1. 사용자별 다른 제한 (프리미엄 사용자는 더 많이)
// 2. API 엔드포인트별 다른 제한
// 3. 시간대별 동적 제한
// 4. Rate Limit 정보를 헤더로 반환:
//    X-RateLimit-Limit: 60
//    X-RateLimit-Remaining: 45
//    X-RateLimit-Reset: 1234567890


// ============================================
// 💡 테스트
// ============================================
//
// 1. Filter 등록 (WebMvcConfigurer)
// 2. 빠르게 70번 요청
// 3. 60번째까지 성공, 61번째부터 429 확인
// 4. 1분 대기 후 다시 요청 가능 확인
