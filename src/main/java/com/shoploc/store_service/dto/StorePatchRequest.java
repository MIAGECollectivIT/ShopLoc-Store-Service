package com.shoploc.store_service.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;

public class StorePatchRequest {

  @Size(max = 255, message = "Le nom ne doit pas dépasser 255 caractères")
  private String name;

  @Size(max = 255, message = "L'adresse ne doit pas dépasser 255 caractères")
  private String address;

  @Size(max = 255, message = "La catégorie ne doit pas dépasser 255 caractères")
  private String category;

  private String rib;

  @Size(max = 500, message = "L'URL ne doit pas dépasser 500 caractères")
  private String url;

  private String openingHours;

  private Instant absenceStartDate;

  private Instant absenceEndDate;

  @Size(max = 255, message = "Le motif d'absence ne doit pas dépasser 255 caractères")
  private String absenceReason;

  private Boolean clearAbsence;

  private Long userId;

  private java.util.List<StoreAbsenceRequest> absences;

  public StorePatchRequest() {}

  public StorePatchRequest(
      String name,
      String address,
      String category,
      String rib,
      String url,
      String openingHours,
      Instant absenceStartDate,
      Instant absenceEndDate,
      String absenceReason) {
    this.name = name;
    this.address = address;
    this.category = category;
    this.rib = rib;
    this.url = url;
    this.openingHours = openingHours;
    this.absenceStartDate = absenceStartDate;
    this.absenceEndDate = absenceEndDate;
    this.absenceReason = absenceReason;
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

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
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

  public Boolean getClearAbsence() {
    return clearAbsence;
  }

  public void setClearAbsence(Boolean clearAbsence) {
    this.clearAbsence = clearAbsence;
  }

  public java.util.List<StoreAbsenceRequest> getAbsences() {
    return absences;
  }

  public void setAbsences(java.util.List<StoreAbsenceRequest> absences) {
    this.absences = absences;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }
}
