package com.shoploc.store_service.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shops")
public class Store {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "uid", nullable = false, unique = true, updatable = false)
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

  @Column(name = "owner_uid")
  private UUID ownerUid;

  @Column(name = "opening_hours", columnDefinition = "TEXT")
  private String openingHours;

  @Column(name = "latitude")
  private Double latitude;

  @Column(name = "longitude")
  private Double longitude;

  @Column(name = "absence_start_date")
  private Instant absenceStartDate;

  @Column(name = "absence_end_date")
  private Instant absenceEndDate;

  @Column(name = "absence_reason", length = 255)
  private String absenceReason;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private StoreStatus status = StoreStatus.ACTIVE;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoked_by", length = 255)
  private String revokedBy;

  @OneToMany(
      mappedBy = "store",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("startDate ASC")
  private List<StoreAbsence> absences = new ArrayList<>();

  public Store() {
    this.uid = UUID.randomUUID();
  }

  public Store(
      String name, String address, String url, String category, String rib, UUID ownerUid) {
    this();
    this.name = name;
    this.address = address;
    this.url = url;
    this.category = category;
    this.rib = rib;
    this.ownerUid = ownerUid;
  }

  public Store(
      UUID uid,
      String name,
      String address,
      String url,
      String category,
      String rib,
      UUID ownerUid) {
    this.uid = uid != null ? uid : UUID.randomUUID();
    this.name = name;
    this.address = address;
    this.url = url;
    this.category = category;
    this.rib = rib;
    this.ownerUid = ownerUid;
  }

  @PrePersist
  public void prePersist() {
    if (this.uid == null) {
      this.uid = UUID.randomUUID();
    }
    if (this.status == null) {
      this.status = StoreStatus.ACTIVE;
    }
  }

  public boolean isCurrentlyAbsent() {
    Instant now = Instant.now();
    if (absences != null && !absences.isEmpty()) {
      return absences.stream().anyMatch(a -> a.isActiveAt(now));
    }
    if (absenceStartDate == null) {
      return false;
    }
    boolean afterStart = !now.isBefore(absenceStartDate);
    boolean beforeEnd = absenceEndDate == null || !now.isAfter(absenceEndDate);
    return afterStart && beforeEnd;
  }

  public Long getAbsenceDurationDays() {
    Instant now = Instant.now();
    if (absences != null && !absences.isEmpty()) {
      StoreAbsence active =
          absences.stream().filter(a -> a.isActiveAt(now)).findFirst().orElse(null);
      if (active != null) {
        return active.getDurationDays();
      }
      StoreAbsence upcoming =
          absences.stream()
              .filter(a -> a.getStartDate() != null && a.getStartDate().isAfter(now))
              .min(Comparator.comparing(StoreAbsence::getStartDate))
              .orElse(null);
      if (upcoming != null) {
        return upcoming.getDurationDays();
      }
    }
    if (absenceStartDate == null || absenceEndDate == null) {
      return null;
    }
    long days =
        ChronoUnit.DAYS.between(
            absenceStartDate.atZone(ZoneOffset.UTC).toLocalDate(),
            absenceEndDate.atZone(ZoneOffset.UTC).toLocalDate());
    return Math.max(0L, days);
  }

  public List<StoreAbsence> getAbsences() {
    return absences;
  }

  public void setAbsences(List<StoreAbsence> absences) {
    this.absences = absences != null ? absences : new ArrayList<>();
    syncCurrentAbsenceFields();
  }

  public void addAbsence(StoreAbsence absence) {
    if (this.absences == null) {
      this.absences = new ArrayList<>();
    }
    absence.setStore(this);
    this.absences.add(absence);
    syncCurrentAbsenceFields();
  }

  public void removeAbsence(StoreAbsence absence) {
    if (this.absences != null) {
      this.absences.remove(absence);
      absence.setStore(null);
      syncCurrentAbsenceFields();
    }
  }

  public void clearAbsences() {
    if (this.absences != null) {
      this.absences.clear();
    }
    syncCurrentAbsenceFields();
  }

  public void syncCurrentAbsenceFields() {
    Instant now = Instant.now();
    if (absences == null || absences.isEmpty()) {
      this.absenceStartDate = null;
      this.absenceEndDate = null;
      this.absenceReason = null;
      return;
    }
    StoreAbsence active = absences.stream().filter(a -> a.isActiveAt(now)).findFirst().orElse(null);
    if (active != null) {
      this.absenceStartDate = active.getStartDate();
      this.absenceEndDate = active.getEndDate();
      this.absenceReason = active.getReason();
      return;
    }
    StoreAbsence upcoming =
        absences.stream()
            .filter(a -> a.getStartDate() != null && a.getStartDate().isAfter(now))
            .min(Comparator.comparing(StoreAbsence::getStartDate))
            .orElse(null);
    if (upcoming != null) {
      this.absenceStartDate = upcoming.getStartDate();
      this.absenceEndDate = upcoming.getEndDate();
      this.absenceReason = upcoming.getReason();
      return;
    }
    StoreAbsence latest =
        absences.stream().max(Comparator.comparing(StoreAbsence::getStartDate)).orElse(null);
    if (latest != null) {
      this.absenceStartDate = latest.getStartDate();
      this.absenceEndDate = latest.getEndDate();
      this.absenceReason = latest.getReason();
    } else {
      this.absenceStartDate = null;
      this.absenceEndDate = null;
      this.absenceReason = null;
    }
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

  public UUID getOwnerUid() {
    return ownerUid;
  }

  public void setOwnerUid(UUID ownerUid) {
    this.ownerUid = ownerUid;
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

  public StoreStatus getStatus() {
    return status;
  }

  public void setStatus(StoreStatus status) {
    this.status = status;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }

  public String getRevokedBy() {
    return revokedBy;
  }

  public void setRevokedBy(String revokedBy) {
    this.revokedBy = revokedBy;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  public boolean isRevoked() {
    return this.status == StoreStatus.REVOKED;
  }
}
