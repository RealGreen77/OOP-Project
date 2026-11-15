package com.itsm.web.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Shared response shapes. */
public final class CommonDtos {

    private CommonDtos() {
    }

    /** Lightweight reference to another resource (id + code + name). */
    public record Ref(Long id, String code, String name) {}

    public record UserRef(Long id, String userCode, String fullName, String role) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                    page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }

    public record MessageResponse(String message) {}
}
