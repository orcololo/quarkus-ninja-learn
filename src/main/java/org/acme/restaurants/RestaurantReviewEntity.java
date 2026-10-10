package org.acme.restaurants;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "restaurant_review")
public class RestaurantReviewEntity extends PanacheEntity {
  @Column(name = "restaurant_id", nullable = false)
  private Long restaurantId;

  @Column(name = "reviewer_subject", nullable = false, columnDefinition = "text")
  private String reviewerSubject;

  @Column(nullable = false)
  private int rating;

  @Column(length = 2000)
  private String comment;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public Long getId() {
    return id;
  }

  public Long getRestaurantId() {
    return restaurantId;
  }

  public void setRestaurantId(Long restaurantId) {
    this.restaurantId = restaurantId;
  }

  public String getReviewerSubject() {
    return reviewerSubject;
  }

  public void setReviewerSubject(String reviewerSubject) {
    this.reviewerSubject = reviewerSubject;
  }

  public int getRating() {
    return rating;
  }

  public void setRating(int rating) {
    this.rating = rating;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
