package com.shoploc.store_service.dto;

import com.shoploc.store_service.Store;
import java.time.Instant;
import java.util.UUID;

public class StoreResponse {

  private Long id;
  private UUID uid;
  private String name;
  private String address;
  private String url;
  private String category;
  private String rib;
  private Long userId;
  private String openingHours;
  private Instant absenceStartDate;
  private Instant absenceEndDate;
  private String absenceReason;
  private Boolean isCurrentlyAbsent;
  private Long absenceDurationDays;

  public StoreResponse() {}

  public StoreResponse(
      Long id,
      UUID uid,
      String name,
      String address,
      String url,
      String category,
      String rib,
      Long userId) {
    this.id = id;
    this.uid = uid;
    this.name = name;
    this.address = address;
    this.url = url;
    this.category = category;
    this.rib = rib;
    this.userId = userId;
  }

  // Backward-compatible constructor for existing tests
  public StoreResponse(
      Long id, String name, String address, String url, String category, String rib, Long userId) {
    this(id, null, name, address, url, category, rib, userId);
  }

  public static StoreResponse fromEntity(Store store) {
    if (store == null) {
      return null;
    }
    StoreResponse response =
        new StoreResponse(
            store.getId(),
            store.getUid(),
            store.getName(),
            store.getAddress(),
            store.getUrl(),
            store.getCategory(),
            store.getRib(),
            store.getUserId());
    response.setOpeningHours(store.getOpeningHours());
    response.setAbsenceStartDate(store.getAbsenceStartDate());
    response.setAbsenceEndDate(store.getAbsenceEndDate());
    response.setAbsenceReason(store.getAbsenceReason());
    response.setIsCurrentlyAbsent(store.isCurrentlyAbsent());
    response.setAbsenceDurationDays(store.getAbsenceDurationDays());
    return response;
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

  public Boolean getIsCurrentlyAbsent() {
    return isCurrentlyAbsent;
  }

  public void setIsCurrentlyAbsent(Boolean isCurrentlyAbsent) {
    this.isCurrentlyAbsent = isCurrentlyAbsent;
  }

  public Long getAbsenceDurationDays() {
    return absenceDurationDays;
  }

  public void setAbsenceDurationDays(Long absenceDurationDays) {
    this.absenceDurationDays = absenceDurationDays;
  }
}
