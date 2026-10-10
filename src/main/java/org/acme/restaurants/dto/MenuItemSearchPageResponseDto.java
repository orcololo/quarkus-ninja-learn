package org.acme.restaurants.dto;

import java.util.List;

public record MenuItemSearchPageResponseDto(
    List<MenuItemSearchResultDto> results,
    int page,
    int size,
    long total) {
}
