package org.acme.missions;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
public class MissionEntity extends PanacheEntity {
  @NotBlank
  @Size(max = 120)
  @Column(nullable = false, length = 120)
  private String title;

  @Size(max = 1000)
  @Column(length = 1000)
  private String description;

  @Min(1)
  @Column(nullable = false)
  private int points;

  @Column(nullable = false)
  private boolean active = true;

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public int getPoints() {
    return points;
  }

  public void setPoints(int points) {
    this.points = points;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
