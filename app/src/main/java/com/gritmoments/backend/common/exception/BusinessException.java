package com.gritmoments.backend.common.exception;

/**
 * 비즈니스 규칙 위반 시 발생하는 예외
 * HTTP 400 응답으로 매핑됩니다.
 * 예: 재고 부족, 잘못된 상태 전이, 중복 요청 등
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
