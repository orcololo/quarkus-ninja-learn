package org.acme.restaurants;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RestaurantOpeningPeriodRepository implements PanacheRepository<RestaurantOpeningPeriodEntity> {
  public List<RestaurantOpeningPeriodEntity> listForRestaurants(List<Long> restaurantIds) {
    return restaurantIds.isEmpty()
        ? List.of()
        : list("restaurantId in ?1 order by restaurantId, dayOfWeek, opensAt", restaurantIds);
  }

  public List<RestaurantOpeningPeriodEntity> listForRestaurant(long restaurantId) {
    return list("restaurantId = ?1 order by dayOfWeek, opensAt", restaurantId);
  }
}
