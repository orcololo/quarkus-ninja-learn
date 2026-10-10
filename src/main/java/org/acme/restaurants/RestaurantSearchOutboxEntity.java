package org.acme.restaurants;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "restaurant_search_outbox")
public class RestaurantSearchOutboxEntity extends PanacheEntity {
  @Column(name = "aggregate_type", nullable = false, length = 16)
  @Enumerated(EnumType.STRING)
  private RestaurantSearchAggregateType aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private Long aggregateId;

  public Long getId() {
    return id;
  }

  public RestaurantSearchAggregateType getAggregateType() {
    return aggregateType;
  }

  public void setAggregateType(RestaurantSearchAggregateType aggregateType) {
    this.aggregateType = aggregateType;
  }

  public Long getAggregateId() {
    return aggregateId;
  }

  public void setAggregateId(Long aggregateId) {
    this.aggregateId = aggregateId;
  }
}
