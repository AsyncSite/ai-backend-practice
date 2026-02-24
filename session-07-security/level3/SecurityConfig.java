package com.gritmoments.backend.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * [Session 07 - Level 3] Spring Security 보안 설정 직접 구성
 *
 * 웹 애플리케이션 보안의 핵심 설정을 직접 구성합니다:
 *   1. CORS - Cross-Origin Resource Sharing (다른 도메인에서의 API 호출 허용)
 *   2. CSRF - Cross-Site Request Forgery (사이트 간 요청 위조 방지)
 *   3. 세션 관리 - Stateless (JWT 기반이므로 서버 세션 사용 안 함)
 *   4. 요청 권한 - URL별 접근 권한 설정
 *   5. JWT 필터 - 토큰 검증 필터를 필터 체인에 추가
 *   6. 비밀번호 인코더 - BCrypt 해싱
 *
 * TODO: 아래의 TODO 부분을 채워서 보안 설정을 완성하세요.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // JWT 인증 필터 (별도 구현 필요 - 이 실습에서는 주석 처리)
    // private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // =========================================================================
    // 1단계: 보안 필터 체인 구성
    // =========================================================================

    /**
     * Spring Security 필터 체인 설정
     *
     * HTTP 요청이 들어오면 이 필터 체인을 순서대로 통과합니다.
     * 각 설정이 하나의 보안 레이어를 담당합니다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // TODO 1: CORS 설정을 적용하세요
        //
        // CORS(Cross-Origin Resource Sharing):
        //   프론트엔드(localhost:3000)에서 백엔드(localhost:8080)로
        //   API를 호출하려면 CORS 허용이 필요합니다.
        //   (브라우저의 동일 출처 정책 때문)
        //
        // 힌트:
        //   http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

        // TODO 2: CSRF 비활성화
        //
        // CSRF(Cross-Site Request Forgery):
        //   세션 쿠키 기반 인증에서 필요한 보호입니다.
        //   REST API는 JWT 토큰을 사용하므로 CSRF가 불필요합니다.
        //   (토큰은 쿠키가 아닌 Authorization 헤더로 전송)
        //
        // 힌트:
        //   http.csrf(csrf -> csrf.disable());

        // TODO 3: 세션 관리 설정 - Stateless
        //
        // JWT 기반 인증은 서버에 세션을 저장하지 않습니다.
        //   - STATELESS: 서버가 세션을 생성하지도, 사용하지도 않음
        //   - 모든 요청에 JWT 토큰을 포함하여 인증
        //   - 수평 확장(서버 추가)이 용이 (세션 공유 불필요)
        //
        // 힌트:
        //   http.sessionManagement(session ->
        //       session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // TODO 4: URL별 접근 권한 설정
        //
        // 누구에게 어떤 API 접근을 허용할지 결정합니다:
        //   - 인증 없이 접근 가능: 헬스체크, Swagger, 로그인/회원가입
        //   - 인증 필요: 나머지 모든 API
        //
        // 힌트:
        //   http.authorizeHttpRequests(auth -> auth
        //       // Actuator 엔드포인트 (헬스체크, 메트릭 등) - 항상 허용
        //       .requestMatchers("/actuator/**").permitAll()
        //       // Swagger UI - 항상 허용
        //       .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
        //       // 인증 관련 API (로그인, 회원가입) - 항상 허용
        //       .requestMatchers("/api/auth/**").permitAll()
        //       // 나머지 모든 요청 - 인증 필요
        //       .anyRequest().authenticated()
        //   );

        // TODO 5: JWT 인증 필터를 필터 체인에 추가하세요
        //
        // UsernamePasswordAuthenticationFilter 앞에 JWT 필터를 추가합니다.
        // 모든 요청에서 Authorization 헤더의 JWT 토큰을 먼저 검증합니다.
        //
        // 힌트 (JwtAuthenticationFilter 구현 후 주석 해제):
        //   http.addFilterBefore(jwtAuthenticationFilter,
        //       UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // =========================================================================
    // 2단계: CORS 설정
    // =========================================================================

    /**
     * CORS 설정 소스
     *
     * 어떤 출처(Origin)에서 어떤 HTTP 메서드와 헤더를 허용할지 설정합니다.
     */
    // TODO 6: CORS 설정을 구성하세요
    //
    // 힌트:
    //   @Bean
    //   public CorsConfigurationSource corsConfigurationSource() {
    //       CorsConfiguration config = new CorsConfiguration();
    //
    //       // 허용할 출처 (프론트엔드 주소)
    //       config.setAllowedOrigins(List.of(
    //           "http://localhost:3000",    // React 개발 서버
    //           "http://localhost:5173"     // Vite 개발 서버
    //       ));
    //
    //       // 허용할 HTTP 메서드
    //       config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    //
    //       // 허용할 헤더 (Authorization: Bearer <token>)
    //       config.setAllowedHeaders(List.of("*"));
    //
    //       // 인증 정보 포함 허용 (쿠키, Authorization 헤더 등)
    //       config.setAllowCredentials(true);
    //
    //       // preflight 요청 캐시 시간 (초)
    //       config.setMaxAge(3600L);
    //
    //       UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    //       source.registerCorsConfiguration("/**", config);
    //       return source;
    //   }

    // =========================================================================
    // 3단계: 비밀번호 인코더
    // =========================================================================

    /**
     * BCrypt 비밀번호 인코더
     *
     * 비밀번호를 안전하게 저장하기 위한 단방향 해시 함수입니다.
     * - BCrypt: 솔트(salt)를 자동 생성하여 레인보우 테이블 공격 방지
     * - 같은 비밀번호라도 매번 다른 해시값 생성
     * - 단방향: 해시값으로 원래 비밀번호를 알 수 없음
     *
     * 예시:
     *   "password123" -> "$2a$10$N9qo8uLOickgx2ZMRZoMye..."  (매번 다른 값)
     *   "password123" -> "$2a$10$Xj3kL8mQp9rTvNwYhS2Poe..."  (매번 다른 값)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // TODO 7: BCryptPasswordEncoder를 반환하세요
        //
        // 힌트:
        //   return new BCryptPasswordEncoder();

        return null; // TODO: 이 줄을 삭제하고 위 TODO를 구현하세요
    }
}
