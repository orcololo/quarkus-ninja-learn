package org.acme.restaurants.dto;

import java.util.List;

public record RestaurantSearchPageResponseDto(
    List<RestaurantResponseDto> restaurants,
    int page,
    int size,
    long total) {
}
