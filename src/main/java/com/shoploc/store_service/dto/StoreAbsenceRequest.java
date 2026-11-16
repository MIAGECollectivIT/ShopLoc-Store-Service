package com.shoploc.store_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public class StoreAbsenceRequest {

  private Long id;

  @NotNull(message = "La date de début est obligatoire")
  private Instant startDate;

  private Instant endDate;

  @Size(max = 255, message = "Le motif d'absence ne doit pas dépasser 255 caractères")
  private String reason;

  public StoreAbsenceRequest() {}

  public StoreAbsenceRequest(Long id, Instant startDate, Instant endDate, String reason) {
    this.id = id;
    this.startDate = startDate;
    this.endDate = endDate;
    this.reason = reason;
  }

  public StoreAbsenceRequest(Instant startDate, Instant endDate, String reason) {
    this(null, startDate, endDate, reason);
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
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
}
