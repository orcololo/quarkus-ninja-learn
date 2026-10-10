package org.acme.restaurants;

import java.util.List;

record RestaurantSearchDocument(
    long id,
    String name,
    String description,
    String address,
    String city,
    String cuisine,
    double latitude,
    double longitude,
    double averageRating,
    long reviewCount,
    boolean openingHoursProvided,
    List<OpeningInterval> openingPeriods,
    Location location) {
  record Location(double lat, double lon) {
  }

  record OpeningInterval(int dayOfWeek, int opensMinute, int closesMinute, String timeZone) {
  }

  static RestaurantSearchDocument from(
      RestaurantEntity restaurant, List<RestaurantOpeningPeriodEntity> openingPeriods) {
    return new RestaurantSearchDocument(
        restaurant.getId(),
        restaurant.getName(),
        restaurant.getDescription(),
        restaurant.getAddress(),
        restaurant.getCity(),
        restaurant.getCuisine().name(),
        restaurant.getLatitude(),
        restaurant.getLongitude(),
        restaurant.getAverageRating(),
        restaurant.getReviewCount(),
        restaurant.getTimeZone() != null,
        openingPeriods.stream()
            .map(period -> new OpeningInterval(
                period.getDayOfWeek().getValue(),
                period.getOpensAt().getHour() * 60 + period.getOpensAt().getMinute(),
                period.getClosesAt().getHour() * 60 + period.getClosesAt().getMinute(),
                restaurant.getTimeZone()))
            .toList(),
        new Location(restaurant.getLatitude(), restaurant.getLongitude()));
  }
}
