package org.acme.restaurants.dto;

import org.acme.restaurants.CuisineType;

public record RestaurantResponseDto(
    Long id,
    String name,
    String description,
    String address,
    String city,
    CuisineType cuisine,
    double latitude,
    double longitude,
    double averageRating,
    long reviewCount,
    OpeningHoursDto openingHours) {
}
