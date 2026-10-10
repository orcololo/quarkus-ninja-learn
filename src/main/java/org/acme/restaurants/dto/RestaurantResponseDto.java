package org.acme.restaurants.dto;

public record RestaurantResponseDto(
    Long id,
    String name,
    String description,
    String address,
    String city,
    String cuisine,
    double latitude,
    double longitude,
    double averageRating,
    long reviewCount) {
}
