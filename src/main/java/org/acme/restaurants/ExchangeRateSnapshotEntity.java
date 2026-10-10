package org.acme.restaurants;

import java.time.Instant;
import java.time.LocalDate;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "restaurant_exchange_rate_snapshot")
public class ExchangeRateSnapshotEntity extends PanacheEntityBase {
  @Id
  private Long id;

  @Column(name = "rate_date", nullable = false)
  private LocalDate rateDate;

  @Column(name = "fetched_at", nullable = false)
  private Instant fetchedAt;

  @Column(name = "rates_json", nullable = false, columnDefinition = "text")
  private String ratesJson;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public LocalDate getRateDate() {
    return rateDate;
  }

  public void setRateDate(LocalDate rateDate) {
    this.rateDate = rateDate;
  }

  public Instant getFetchedAt() {
    return fetchedAt;
  }

  public void setFetchedAt(Instant fetchedAt) {
    this.fetchedAt = fetchedAt;
  }

  public String getRatesJson() {
    return ratesJson;
  }

  public void setRatesJson(String ratesJson) {
    this.ratesJson = ratesJson;
  }
}
