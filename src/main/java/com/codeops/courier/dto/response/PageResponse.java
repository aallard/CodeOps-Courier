package com.codeops.courier.dto.response;

import java.util.List;

/**
 * Generic paginated response wrapper. Converts Spring's Page into a flat DTO.
 *
 * @param <T> the type of content items
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean isLast
) {

    /**
     * Creates a PageResponse from a Spring Page object.
     *
     * @param springPage the Spring Page to convert
     * @param <T>        the content type
     * @return a PageResponse wrapping the page data
     */
    public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> springPage) {
        return new PageResponse<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.isLast()
        );
    }
}
