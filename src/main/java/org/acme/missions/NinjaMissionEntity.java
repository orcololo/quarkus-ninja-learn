package org.acme.missions;

import java.time.Instant;

import org.acme.ninjas.NinjaEntity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
    name = "ninja_mission_assignment_unique",
    columnNames = { "ninja_id", "mission_id" }))
public class NinjaMissionEntity extends PanacheEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ninja_id", nullable = false)
  private NinjaEntity ninja;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "mission_id", nullable = false)
  private MissionEntity mission;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private NinjaMissionStatus status = NinjaMissionStatus.ASSIGNED;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "decided_at")
  private Instant decidedAt;

  public Long getId() {
    return id;
  }

  public NinjaEntity getNinja() {
    return ninja;
  }

  public void setNinja(NinjaEntity ninja) {
    this.ninja = ninja;
  }

  public MissionEntity getMission() {
    return mission;
  }

  public void setMission(MissionEntity mission) {
    this.mission = mission;
  }

  public NinjaMissionStatus getStatus() {
    return status;
  }

  public void setStatus(NinjaMissionStatus status) {
    this.status = status;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }

  public void setAssignedAt(Instant assignedAt) {
    this.assignedAt = assignedAt;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public void setSubmittedAt(Instant submittedAt) {
    this.submittedAt = submittedAt;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public void setDecidedAt(Instant decidedAt) {
    this.decidedAt = decidedAt;
  }
}
