package pl.romcio.driperska.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** Uniform pagination envelope returned by all list endpoints. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public <R> PageResponse<R> map(java.util.function.Function<T, R> mapper) {
        return new PageResponse<>(
                content.stream().map(mapper).toList(),
                page, size, totalElements, totalPages);
    }
}
