package com.gritmoments.backend.auth.controller;

import com.gritmoments.backend.common.dto.ApiResponse;
import com.gritmoments.backend.user.entity.User;
import com.gritmoments.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 인증 API 컨트롤러 (세션 07: 인증/인가)
 *
 * REST API 설계:
 * - POST /api/auth/login: 로그인 (이메일/비밀번호)
 * - GET /api/auth/hash: BCrypt 해시 생성 (세션 07 레벨 2 데모용)
 * - POST /api/auth/verify: BCrypt 해시 검증 (세션 07 레벨 2 데모용)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final UserService userService;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 로그인 (세션 07: 기본 인증)
     * POST /api/auth/login
     *
     * 레벨 3에서 JWT로 업그레이드 예정
     */
    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인합니다. 성공 시 토큰을 반환합니다.")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        // 사용자 조회
        User user = userService.findByEmail(request.email());

        // 비밀번호 검증
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("비밀번호가 일치하지 않습니다"));
        }

        // 토큰 생성 (레벨 3에서 JWT로 교체)
        String token = UUID.randomUUID().toString();

        LoginResponse response = new LoginResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name()
        );

        return ResponseEntity.ok(ApiResponse.ok(response, "로그인 성공"));
    }

    /**
     * BCrypt 해시 생성 (세션 07 레벨 2: BCrypt 이해)
     * GET /api/auth/hash?password=mypassword
     *
     * 데모용 엔드포인트: 평문 비밀번호를 BCrypt 해시로 변환
     */
    @GetMapping("/hash")
    @Operation(summary = "BCrypt 해시 생성", description = "평문 비밀번호를 BCrypt 해시로 변환합니다 (교육용)")
    public ResponseEntity<ApiResponse<HashResponse>> hashPassword(@RequestParam String password) {
        String hash = passwordEncoder.encode(password);
        return ResponseEntity.ok(ApiResponse.ok(
                new HashResponse(password, hash),
                "해시 생성 완료"
        ));
    }

    /**
     * BCrypt 해시 검증 (세션 07 레벨 2: BCrypt 이해)
     * POST /api/auth/verify
     *
     * 데모용 엔드포인트: 평문 비밀번호와 해시가 일치하는지 검증
     */
    @PostMapping("/verify")
    @Operation(summary = "BCrypt 해시 검증", description = "평문 비밀번호와 BCrypt 해시가 일치하는지 검증합니다 (교육용)")
    public ResponseEntity<ApiResponse<VerifyResponse>> verifyPassword(@RequestBody VerifyRequest request) {
        boolean matches = passwordEncoder.matches(request.password(), request.hash());
        return ResponseEntity.ok(ApiResponse.ok(
                new VerifyResponse(matches, request.password(), request.hash()),
                matches ? "비밀번호 일치" : "비밀번호 불일치"
        ));
    }

    /**
     * 로그인 요청 DTO
     */
    public record LoginRequest(
            String email,
            String password
    ) {}

    /**
     * 로그인 응답 DTO
     */
    public record LoginResponse(
            String token,
            Long userId,
            String email,
            String name,
            String role
    ) {}

    /**
     * 해시 응답 DTO
     */
    public record HashResponse(
            String originalPassword,
            String bcryptHash
    ) {}

    /**
     * 검증 요청 DTO
     */
    public record VerifyRequest(
            String password,
            String hash
    ) {}

    /**
     * 검증 응답 DTO
     */
    public record VerifyResponse(
            boolean matches,
            String password,
            String hash
    ) {}
}
