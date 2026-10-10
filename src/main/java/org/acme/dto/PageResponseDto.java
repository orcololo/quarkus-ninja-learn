package org.acme.dto;

import java.util.List;

public record PageResponseDto<T>(
    List<T> items,
    int page,
    int size,
    long total) {
}
