package org.acme.restaurants;

import java.math.BigDecimal;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "restaurant_menu_item")
public class MenuItemEntity extends PanacheEntity {
  @Column(name = "restaurant_id", nullable = false)
  private Long restaurantId;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 2000)
  private String description;

  @Column(length = 100)
  private String category;

  @Column(nullable = false, precision = 12, scale = 3)
  private BigDecimal price;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "is_available", nullable = false)
  private boolean available;

  @Column(name = "owner_subject", length = 255)
  private String ownerSubject;

  public Long getId() {
    return id;
  }

  public Long getRestaurantId() {
    return restaurantId;
  }

  public void setRestaurantId(Long restaurantId) {
    this.restaurantId = restaurantId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public boolean isAvailable() {
    return available;
  }

  public void setAvailable(boolean available) {
    this.available = available;
  }

  public String getOwnerSubject() {
    return ownerSubject;
  }

  public void setOwnerSubject(String ownerSubject) {
    this.ownerSubject = ownerSubject;
  }
}
