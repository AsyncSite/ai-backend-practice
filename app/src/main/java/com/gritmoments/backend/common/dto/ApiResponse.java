package com.gritmoments.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * API 공통 응답 형식 (세션 12: API 설계)
 *
 * 모든 API 응답을 일관된 형식으로 감쌉니다.
 * {
 *   "success": true,
 *   "data": { ... },
 *   "message": "조회 성공"
 * }
 *
 * @param <T> 응답 데이터 타입
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message
) {
    /** 성공 응답 (데이터 포함) */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 성공 응답 (메시지 포함) */
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    /** 실패 응답 */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
