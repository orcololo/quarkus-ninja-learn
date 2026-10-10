package org.acme.restaurants;

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
    Location location) {
  record Location(double lat, double lon) {
  }

  static RestaurantSearchDocument from(RestaurantEntity restaurant) {
    return new RestaurantSearchDocument(
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
        new Location(restaurant.getLatitude(), restaurant.getLongitude()));
  }
}
