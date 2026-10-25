package com.iotiq.partnererp.common.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * List payload for paginated endpoints. {@link #meta()} is placed in the envelope's {@code meta} field.
 */
public record PageResponse<T>(List<T> content, PageMeta meta) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(),
                new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }

    public ApiResponse<List<T>> toApiResponse() {
        return ApiResponse.ok(content, meta);
    }

    public record PageMeta(int page, int size, long totalElements, int totalPages) {
    }
}
