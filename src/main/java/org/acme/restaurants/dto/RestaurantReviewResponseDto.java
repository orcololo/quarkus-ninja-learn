package org.acme.restaurants.dto;

import java.time.Instant;

public record RestaurantReviewResponseDto(
    Long id,
    Long restaurantId,
    int rating,
    String comment,
    Instant createdAt) {
}
