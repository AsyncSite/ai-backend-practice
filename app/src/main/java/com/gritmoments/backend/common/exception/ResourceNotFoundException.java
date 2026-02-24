package com.gritmoments.backend.common.exception;

/**
 * 리소스를 찾을 수 없을 때 발생하는 예외
 * HTTP 404 응답으로 매핑됩니다.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " not found with id: " + id);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(resourceName + " not found with " + fieldName + ": " + fieldValue);
    }
}
