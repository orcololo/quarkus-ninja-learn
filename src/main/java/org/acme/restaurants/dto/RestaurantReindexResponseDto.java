package org.acme.restaurants.dto;

public record RestaurantReindexResponseDto(
    Long id,
    long restaurantsQueued,
    long menuItemsQueued,
    boolean complete) {
}
