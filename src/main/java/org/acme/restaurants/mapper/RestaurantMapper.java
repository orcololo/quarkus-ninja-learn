package org.acme.restaurants.mapper;

import org.acme.restaurants.RestaurantEntity;
import org.acme.restaurants.RestaurantOpeningPeriodEntity;
import org.acme.restaurants.RestaurantReviewEntity;
import org.acme.restaurants.MenuItemEntity;
import org.acme.restaurants.dto.OpeningHoursDto;
import org.acme.restaurants.dto.OpeningPeriodDto;
import org.acme.restaurants.dto.MenuItemResponseDto;
import org.acme.restaurants.dto.RestaurantResponseDto;
import org.acme.restaurants.dto.RestaurantReviewResponseDto;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class RestaurantMapper {
  public RestaurantResponseDto toResponse(
      RestaurantEntity restaurant, List<RestaurantOpeningPeriodEntity> openingPeriods) {
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
        restaurant.getReviewCount(),
        restaurant.getTimeZone() == null
            ? null
            : new OpeningHoursDto(
                restaurant.getTimeZone(),
                openingPeriods.stream()
                    .map(period -> new OpeningPeriodDto(
                        period.getDayOfWeek(), period.getOpensAt(), period.getClosesAt()))
                    .toList()));
  }

  public RestaurantReviewResponseDto toResponse(RestaurantReviewEntity review) {
    return new RestaurantReviewResponseDto(
        review.getId(),
        review.getRestaurantId(),
        review.getRating(),
        review.getComment(),
        review.getCreatedAt());
  }

  public MenuItemResponseDto toResponse(MenuItemEntity item) {
    return new MenuItemResponseDto(
        item.getId(),
        item.getRestaurantId(),
        item.getName(),
        item.getDescription(),
        item.getCategory(),
        item.getPrice(),
        item.getCurrency(),
        item.isAvailable());
  }
}
