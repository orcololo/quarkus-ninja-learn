package org.acme.restaurants;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "restaurant_search_outbox")
public class RestaurantSearchOutboxEntity extends PanacheEntity {
  @Column(name = "restaurant_id", nullable = false)
  private Long restaurantId;

  public Long getId() {
    return id;
  }

  public Long getRestaurantId() {
    return restaurantId;
  }

  public void setRestaurantId(Long restaurantId) {
    this.restaurantId = restaurantId;
  }
}
