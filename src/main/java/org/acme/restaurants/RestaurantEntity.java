package org.acme.restaurants;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "restaurant")
public class RestaurantEntity extends PanacheEntity {
  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 2000)
  private String description;

  @Column(nullable = false)
  private String address;

  @Column(nullable = false, length = 120)
  private String city;

  @Column(nullable = false, length = 100)
  @Enumerated(EnumType.STRING)
  private CuisineType cuisine;

  @Column(nullable = false)
  private double latitude;

  @Column(nullable = false)
  private double longitude;

  @Column(name = "average_rating", nullable = false)
  private double averageRating;

  @Column(name = "review_count", nullable = false)
  private long reviewCount;

  @Column(name = "time_zone", length = 64)
  private String timeZone;

  @Column(name = "owner_subject", length = 255)
  private String ownerSubject;

  public Long getId() {
    return id;
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

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public CuisineType getCuisine() {
    return cuisine;
  }

  public void setCuisine(CuisineType cuisine) {
    this.cuisine = cuisine;
  }

  public double getLatitude() {
    return latitude;
  }

  public void setLatitude(double latitude) {
    this.latitude = latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public void setLongitude(double longitude) {
    this.longitude = longitude;
  }

  public double getAverageRating() {
    return averageRating;
  }

  public long getReviewCount() {
    return reviewCount;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  public String getOwnerSubject() {
    return ownerSubject;
  }

  public void setOwnerSubject(String ownerSubject) {
    this.ownerSubject = ownerSubject;
  }
}
