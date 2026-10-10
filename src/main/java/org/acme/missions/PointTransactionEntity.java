package org.acme.missions;

import java.time.Instant;

import org.acme.ninjas.NinjaEntity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
    name = "point_transaction_assignment_unique",
    columnNames = "ninja_mission_id"))
public class PointTransactionEntity extends PanacheEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ninja_id", nullable = false)
  private NinjaEntity ninja;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ninja_mission_id", nullable = false, unique = true)
  private NinjaMissionEntity ninjaMission;

  @Column(nullable = false)
  private int points;

  @Column(name = "granted_at", nullable = false)
  private Instant grantedAt;

  public Long getId() {
    return id;
  }

  public NinjaEntity getNinja() {
    return ninja;
  }

  public void setNinja(NinjaEntity ninja) {
    this.ninja = ninja;
  }

  public NinjaMissionEntity getNinjaMission() {
    return ninjaMission;
  }

  public void setNinjaMission(NinjaMissionEntity ninjaMission) {
    this.ninjaMission = ninjaMission;
  }

  public int getPoints() {
    return points;
  }

  public void setPoints(int points) {
    this.points = points;
  }

  public Instant getGrantedAt() {
    return grantedAt;
  }

  public void setGrantedAt(Instant grantedAt) {
    this.grantedAt = grantedAt;
  }
}
