package org.acme.restaurants;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RestaurantReviewRepository implements PanacheRepository<RestaurantReviewEntity> {
  public List<RestaurantReviewEntity> listForRestaurant(long restaurantId, int page, int size) {
    return find("restaurantId = ?1 order by createdAt desc, id desc", restaurantId)
        .page(Page.of(page, size))
        .list();
  }

  public long countForRestaurant(long restaurantId) {
    return count("restaurantId", restaurantId);
  }

  public RestaurantReviewEntity findForRestaurant(long restaurantId, long reviewId) {
    return find("restaurantId = ?1 and id = ?2", restaurantId, reviewId).firstResult();
  }
}
