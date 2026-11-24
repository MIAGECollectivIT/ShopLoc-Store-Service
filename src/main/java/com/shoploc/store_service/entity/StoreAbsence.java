package com.shoploc.store_service.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "store_absences")
public class StoreAbsence {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "uid", nullable = false, unique = true, updatable = false)
  private UUID uid;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "store_id", nullable = false)
  @JsonIgnore
  private Store store;

  @Column(name = "start_date", nullable = false)
  private Instant startDate;

  @Column(name = "end_date")
  private Instant endDate;

  @Column(name = "reason", length = 255)
  private String reason;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  public StoreAbsence() {
    this.uid = UUID.randomUUID();
  }

  public StoreAbsence(Store store, Instant startDate, Instant endDate, String reason) {
    this();
    this.store = store;
    this.startDate = startDate;
    this.endDate = endDate;
    this.reason = reason;
    this.createdAt = Instant.now();
  }

  @PrePersist
  public void prePersist() {
    if (this.uid == null) {
      this.uid = UUID.randomUUID();
    }
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
  }

  public boolean isActiveAt(Instant instant) {
    if (startDate == null || instant == null) {
      return false;
    }
    boolean afterStart = !instant.isBefore(startDate);
    boolean beforeEnd = endDate == null || !instant.isAfter(endDate);
    return afterStart && beforeEnd;
  }

  public boolean isUpcoming(Instant instant) {
    if (startDate == null || instant == null) {
      return false;
    }
    return startDate.isAfter(instant);
  }

  public boolean isPast(Instant instant) {
    if (endDate != null && instant != null) {
      return instant.isAfter(endDate);
    }
    return false;
  }

  public Long getDurationDays() {
    if (startDate == null || endDate == null) {
      return null;
    }
    long days =
        ChronoUnit.DAYS.between(
            startDate.atZone(ZoneOffset.UTC).toLocalDate(),
            endDate.atZone(ZoneOffset.UTC).toLocalDate());
    return Math.max(0L, days);
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public UUID getUid() {
    return uid;
  }

  public void setUid(UUID uid) {
    this.uid = uid;
  }

  public Store getStore() {
    return store;
  }

  public void setStore(Store store) {
    this.store = store;
  }

  public Instant getStartDate() {
    return startDate;
  }

  public void setStartDate(Instant startDate) {
    this.startDate = startDate;
  }

  public Instant getEndDate() {
    return endDate;
  }

  public void setEndDate(Instant endDate) {
    this.endDate = endDate;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
