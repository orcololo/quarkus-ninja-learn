package org.acme.restaurants.dto;

import java.util.List;

public record RestaurantReviewPageResponseDto(
    List<RestaurantReviewResponseDto> reviews,
    int page,
    int size,
    long total) {
}
