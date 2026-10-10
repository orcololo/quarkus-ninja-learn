package org.acme.restaurants.mapper;

import org.acme.restaurants.RestaurantEntity;
import org.acme.restaurants.RestaurantReviewEntity;
import org.acme.restaurants.dto.RestaurantResponseDto;
import org.acme.restaurants.dto.RestaurantReviewResponseDto;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RestaurantMapper {
  public RestaurantResponseDto toResponse(RestaurantEntity restaurant) {
    return new RestaurantResponseDto(
        restaurant.getId(),
        restaurant.getName(),
        restaurant.getDescription(),
        restaurant.getAddress(),
        restaurant.getCity(),
        restaurant.getCuisine(),
        restaurant.getLatitude(),
        restaurant.getLongitude(),
        restaurant.getAverageRating(),
        restaurant.getReviewCount());
  }

  public RestaurantReviewResponseDto toResponse(RestaurantReviewEntity review) {
    return new RestaurantReviewResponseDto(
        review.getId(),
        review.getRestaurantId(),
        review.getRating(),
        review.getComment(),
        review.getCreatedAt());
  }
}
