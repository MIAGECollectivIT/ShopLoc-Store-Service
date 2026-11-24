package com.shoploc.store_service.dto;

import com.shoploc.store_service.entity.StoreAbsence;
import java.time.Instant;
import java.util.UUID;

public class StoreAbsenceResponse {

  private UUID uid;
  private Instant startDate;
  private Instant endDate;
  private String reason;
  private Long durationDays;
  private Boolean isCurrentlyActive;
  private Boolean isUpcoming;
  private Boolean isPast;
  private Instant createdAt;

  public StoreAbsenceResponse() {}

  public static StoreAbsenceResponse fromEntity(StoreAbsence absence) {
    if (absence == null) {
      return null;
    }
    Instant now = Instant.now();
    StoreAbsenceResponse response = new StoreAbsenceResponse();
    response.setUid(absence.getUid());
    response.setStartDate(absence.getStartDate());
    response.setEndDate(absence.getEndDate());
    response.setReason(absence.getReason());
    response.setDurationDays(absence.getDurationDays());
    response.setIsCurrentlyActive(absence.isActiveAt(now));
    response.setIsUpcoming(absence.isUpcoming(now));
    response.setIsPast(absence.isPast(now));
    response.setCreatedAt(absence.getCreatedAt());
    return response;
  }

  public UUID getUid() {
    return uid;
  }

  public void setUid(UUID uid) {
    this.uid = uid;
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

  public Long getDurationDays() {
    return durationDays;
  }

  public void setDurationDays(Long durationDays) {
    this.durationDays = durationDays;
  }

  public Boolean getIsCurrentlyActive() {
    return isCurrentlyActive;
  }

  public void setIsCurrentlyActive(Boolean currentlyActive) {
    isCurrentlyActive = currentlyActive;
  }

  public Boolean getIsUpcoming() {
    return isUpcoming;
  }

  public void setIsUpcoming(Boolean upcoming) {
    isUpcoming = upcoming;
  }

  public Boolean getIsPast() {
    return isPast;
  }

  public void setIsPast(Boolean past) {
    isPast = past;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
