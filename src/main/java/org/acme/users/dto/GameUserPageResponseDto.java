package org.acme.users.dto;

import java.util.List;

public record GameUserPageResponseDto(
    List<GameUserResponseDto> items,
    int page,
    int size,
    long total) {
}
