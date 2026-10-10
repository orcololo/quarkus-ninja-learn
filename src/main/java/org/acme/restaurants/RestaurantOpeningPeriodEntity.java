package org.acme.restaurants;

import java.time.DayOfWeek;
import java.time.LocalTime;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "restaurant_opening_period")
public class RestaurantOpeningPeriodEntity extends PanacheEntity {
  @Column(name = "restaurant_id", nullable = false)
  private Long restaurantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "day_of_week", nullable = false, length = 9)
  private DayOfWeek dayOfWeek;

  @Column(name = "opens_at", nullable = false)
  private LocalTime opensAt;

  @Column(name = "closes_at", nullable = false)
  private LocalTime closesAt;

  public Long getId() {
    return id;
  }

  public Long getRestaurantId() {
    return restaurantId;
  }

  public void setRestaurantId(Long restaurantId) {
    this.restaurantId = restaurantId;
  }

  public DayOfWeek getDayOfWeek() {
    return dayOfWeek;
  }

  public void setDayOfWeek(DayOfWeek dayOfWeek) {
    this.dayOfWeek = dayOfWeek;
  }

  public LocalTime getOpensAt() {
    return opensAt;
  }

  public void setOpensAt(LocalTime opensAt) {
    this.opensAt = opensAt;
  }

  public LocalTime getClosesAt() {
    return closesAt;
  }

  public void setClosesAt(LocalTime closesAt) {
    this.closesAt = closesAt;
  }
}
