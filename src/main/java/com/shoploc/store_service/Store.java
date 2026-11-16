package com.shoploc.store_service;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "shops")
public class Store {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "uid", nullable = false, unique = true)
  private UUID uid;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(nullable = false, length = 255)
  private String address;

  @Column(length = 500)
  private String url;

  @Column(nullable = false, length = 255)
  private String category;

  @Column(nullable = false, length = 34)
  private String rib;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "opening_hours", columnDefinition = "TEXT")
  private String openingHours;

  @Column(name = "absence_start_date")
  private Instant absenceStartDate;

  @Column(name = "absence_end_date")
  private Instant absenceEndDate;

  @Column(name = "absence_reason", length = 255)
  private String absenceReason;

  public Store() {
    this.uid = UUID.randomUUID();
  }

  public Store(String name, String address, String url, String category, String rib, Long userId) {
    this();
    this.name = name;
    this.address = address;
    this.url = url;
    this.category = category;
    this.rib = rib;
    this.userId = userId;
  }

  public Store(
      UUID uid, String name, String address, String url, String category, String rib, Long userId) {
    this.uid = uid != null ? uid : UUID.randomUUID();
    this.name = name;
    this.address = address;
    this.url = url;
    this.category = category;
    this.rib = rib;
    this.userId = userId;
  }

  @PrePersist
  public void prePersist() {
    if (this.uid == null) {
      this.uid = UUID.randomUUID();
    }
  }

  public boolean isCurrentlyAbsent() {
    if (absenceStartDate == null) {
      return false;
    }
    Instant now = Instant.now();
    boolean afterStart = !now.isBefore(absenceStartDate);
    boolean beforeEnd = absenceEndDate == null || !now.isAfter(absenceEndDate);
    return afterStart && beforeEnd;
  }

  public Long getAbsenceDurationDays() {
    if (absenceStartDate == null || absenceEndDate == null) {
      return null;
    }
    long days =
        ChronoUnit.DAYS.between(
            absenceStartDate.atZone(ZoneOffset.UTC).toLocalDate(),
            absenceEndDate.atZone(ZoneOffset.UTC).toLocalDate());
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getRib() {
    return rib;
  }

  public void setRib(String rib) {
    this.rib = rib;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getOpeningHours() {
    return openingHours;
  }

  public void setOpeningHours(String openingHours) {
    this.openingHours = openingHours;
  }

  public Instant getAbsenceStartDate() {
    return absenceStartDate;
  }

  public void setAbsenceStartDate(Instant absenceStartDate) {
    this.absenceStartDate = absenceStartDate;
  }

  public Instant getAbsenceEndDate() {
    return absenceEndDate;
  }

  public void setAbsenceEndDate(Instant absenceEndDate) {
    this.absenceEndDate = absenceEndDate;
  }

  public String getAbsenceReason() {
    return absenceReason;
  }

  public void setAbsenceReason(String absenceReason) {
    this.absenceReason = absenceReason;
  }
}
