package com.gritmoments.backend.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이지네이션 응답 (세션 12: API 설계)
 *
 * Spring Data의 Page 객체를 API 응답에 적합한 형태로 변환합니다.
 *
 * @param <T> 컨텐츠 항목 타입
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    /** Spring Data Page -> PageResponse 변환 */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
