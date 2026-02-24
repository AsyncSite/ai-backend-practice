package com.gritmoments.backend.user.controller;

import com.gritmoments.backend.common.dto.ApiResponse;
import com.gritmoments.backend.user.entity.User;
import com.gritmoments.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 회원 API 컨트롤러 (세션 07: 인증/인가, 세션 10: 아키텍처)
 *
 * REST API 설계:
 * - POST /api/users/signup: 회원가입
 * - GET /api/users/{id}: 사용자 정보 조회
 * - GET /api/users/me: 현재 로그인 사용자 정보 (세션 07: JWT 인증 후 구현)
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "회원 API")
public class UserController {

    private final UserService userService;

    /**
     * 회원가입 (세션 07: BCrypt 암호화)
     * POST /api/users/signup
     */
    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다. 비밀번호는 BCrypt로 암호화됩니다.")
    public ResponseEntity<ApiResponse<User>> signup(@RequestBody SignupRequest request) {
        User user = userService.signup(
                request.email(),
                request.password(),
                request.name(),
                request.phone()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(user));
    }

    /**
     * 사용자 정보 조회
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "사용자 정보 조회")
    public ResponseEntity<ApiResponse<User>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.findById(id)));
    }

    /**
     * 현재 로그인한 사용자 정보 조회 (세션 07: JWT 인증 후 구현 예정)
     * GET /api/users/me
     */
    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "JWT 토큰으로 인증된 사용자의 정보를 반환합니다 (미구현)")
    public ResponseEntity<ApiResponse<String>> getCurrentUser() {
        // TODO: 세션 07에서 JWT 인증 구현 후 SecurityContext에서 사용자 정보 추출
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.ok("JWT 인증 구현 필요 (세션 07)"));
    }

    /**
     * 회원가입 요청 DTO
     */
    public record SignupRequest(
            String email,
            String password,
            String name,
            String phone
    ) {}
}
