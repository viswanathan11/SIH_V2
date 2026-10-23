package com.vidyasetu.common;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
        int pages = size == 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new PageResponse<>(content, page, size, total, pages);
    }
}
