package org.acme.restaurants;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "restaurant_reindex_job")
public class RestaurantReindexJobEntity extends PanacheEntity {
  @Column(name = "restaurant_after_id", nullable = false)
  private long restaurantAfterId;

  @Column(name = "menu_item_after_id", nullable = false)
  private long menuItemAfterId;

  @Column(name = "restaurants_queued", nullable = false)
  private long restaurantsQueued;

  @Column(name = "menu_items_queued", nullable = false)
  private long menuItemsQueued;

  @Column(name = "restaurants_done", nullable = false)
  private boolean restaurantsDone;

  @Column(name = "menu_items_done", nullable = false)
  private boolean menuItemsDone;

  @Column(nullable = false)
  private boolean complete;

  public Long getId() {
    return id;
  }

  public long getRestaurantAfterId() {
    return restaurantAfterId;
  }

  public void setRestaurantAfterId(long restaurantAfterId) {
    this.restaurantAfterId = restaurantAfterId;
  }

  public long getMenuItemAfterId() {
    return menuItemAfterId;
  }

  public void setMenuItemAfterId(long menuItemAfterId) {
    this.menuItemAfterId = menuItemAfterId;
  }

  public long getRestaurantsQueued() {
    return restaurantsQueued;
  }

  public void setRestaurantsQueued(long restaurantsQueued) {
    this.restaurantsQueued = restaurantsQueued;
  }

  public long getMenuItemsQueued() {
    return menuItemsQueued;
  }

  public void setMenuItemsQueued(long menuItemsQueued) {
    this.menuItemsQueued = menuItemsQueued;
  }

  public boolean isRestaurantsDone() {
    return restaurantsDone;
  }

  public void setRestaurantsDone(boolean restaurantsDone) {
    this.restaurantsDone = restaurantsDone;
  }

  public boolean isMenuItemsDone() {
    return menuItemsDone;
  }

  public void setMenuItemsDone(boolean menuItemsDone) {
    this.menuItemsDone = menuItemsDone;
  }

  public boolean isComplete() {
    return complete;
  }

  public void setComplete(boolean complete) {
    this.complete = complete;
  }
}
