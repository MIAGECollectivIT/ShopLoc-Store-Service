package com.shoploc.store_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoploc.store_service.entity.Store;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Evaluates store opening hours, absences, and closing time windows (SLE-32, SLE-294, SLE-295).
 * Explicitly uses ZoneId.of("Europe/Paris") for all time conversions.
 */
@Component
public class StoreScheduleEvaluator {

  public static final ZoneId PARIS_ZONE = ZoneId.of("Europe/Paris");
  private static final Logger log = LoggerFactory.getLogger(StoreScheduleEvaluator.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  public record ScheduleEvaluation(
      boolean isOpenAtArrival,
      boolean isClosedAllDay,
      long minutesUntilClosing,
      String scheduleInfo,
      String warningAlert) {}

  /**
   * Evaluates if store is open at the estimated arrival instant, detects if closed all day,
   * calculates remaining minutes until closing, and raises warning alerts if < 45m.
   */
  public ScheduleEvaluation evaluateStoreAtTime(Store store, Instant estimatedArrival) {
    if (store == null || estimatedArrival == null) {
      return new ScheduleEvaluation(true, false, Long.MAX_VALUE, "Horaires non spécifiés", null);
    }

    ZonedDateTime arrivalDateTime = estimatedArrival.atZone(PARIS_ZONE);
    LocalDate arrivalDate = arrivalDateTime.toLocalDate();
    LocalTime arrivalTime = arrivalDateTime.toLocalTime();

    // 1. Check if store is in an absence period covering the arrival date
    if (isStoreInAbsence(store, arrivalDate)) {
      String reason =
          store.getAbsenceReason() != null ? store.getAbsenceReason() : "Congés / Fermeture";
      return new ScheduleEvaluation(
          false,
          true,
          0,
          "Fermé exceptionnellement (" + reason + ")",
          "Attention : boutique fermée pour " + reason);
    }

    // 2. Parse daily schedule from openingHours
    DaySchedule daySchedule =
        getDaySchedule(store.getOpeningHours(), arrivalDateTime.getDayOfWeek());

    if (!daySchedule.isOpen()
        || daySchedule.getOpeningTime() == null
        || daySchedule.getClosingTime() == null) {
      return new ScheduleEvaluation(
          false,
          true,
          0,
          "Fermé toute la journée",
          "Attention : cette boutique est fermée toute la journée ("
              + arrivalDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
              + ")");
    }

    String scheduleDisplay =
        daySchedule.getOpeningTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            + " - "
            + daySchedule.getClosingTime().format(DateTimeFormatter.ofPattern("HH:mm"));

    // Check if arrival is before opening
    if (arrivalTime.isBefore(daySchedule.getOpeningTime())) {
      long minutesBeforeOpen =
          Duration.between(arrivalTime, daySchedule.getOpeningTime()).toMinutes();
      return new ScheduleEvaluation(
          false,
          false,
          0,
          scheduleDisplay,
          "Attention : boutique pas encore ouverte à l'arrivée estimée (ouvre à "
              + daySchedule.getOpeningTime()
              + ", dans "
              + minutesBeforeOpen
              + " min)");
    }

    // Check if arrival is after closing
    if (arrivalTime.isAfter(daySchedule.getClosingTime())) {
      return new ScheduleEvaluation(
          false,
          false,
          0,
          scheduleDisplay,
          "Attention : boutique déjà fermée à l'arrivée estimée (fermeture à "
              + daySchedule.getClosingTime()
              + ")");
    }

    // Currently open: calculate minutes remaining before closing
    long minutesUntilClosing =
        Duration.between(arrivalTime, daySchedule.getClosingTime()).toMinutes();
    String warning = null;
    if (minutesUntilClosing < 45) {
      warning =
          "Attention : créneau critique ! Fermeture dans "
              + minutesUntilClosing
              + " min (ferme à "
              + daySchedule.getClosingTime().format(DateTimeFormatter.ofPattern("HH:mm"))
              + ")";
    }

    return new ScheduleEvaluation(true, false, minutesUntilClosing, scheduleDisplay, warning);
  }

  /** Checks whether the store is explicitly closed all day on the target date. */
  public boolean isClosedAllDay(Store store, LocalDate targetDate) {
    if (store == null || targetDate == null) {
      return false;
    }
    if (isStoreInAbsence(store, targetDate)) {
      return true;
    }
    DaySchedule daySchedule = getDaySchedule(store.getOpeningHours(), targetDate.getDayOfWeek());
    return !daySchedule.isOpen();
  }

  private boolean isStoreInAbsence(Store store, LocalDate targetDate) {
    if (store.getAbsences() == null || store.getAbsences().isEmpty()) {
      return false;
    }
    return store.getAbsences().stream()
        .anyMatch(
            absence -> {
              if (absence.getStartDate() == null || absence.getEndDate() == null) {
                return false;
              }
              LocalDate start = absence.getStartDate().atZone(PARIS_ZONE).toLocalDate();
              LocalDate end = absence.getEndDate().atZone(PARIS_ZONE).toLocalDate();
              return !targetDate.isBefore(start) && !targetDate.isAfter(end);
            });
  }

  private DaySchedule getDaySchedule(String openingHours, DayOfWeek dayOfWeek) {
    if (openingHours == null || openingHours.isBlank()) {
      // Default: Mon-Sat 09:00 - 19:00, Sun closed
      if (dayOfWeek == DayOfWeek.SUNDAY) {
        return new DaySchedule(false, null, null);
      }
      return new DaySchedule(true, LocalTime.of(9, 0), LocalTime.of(19, 0));
    }

    String trimmed = openingHours.trim();
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      try {
        JsonNode array = objectMapper.readTree(trimmed);
        String frenchDay = getFrenchDayName(dayOfWeek);
        for (JsonNode item : array) {
          String dayName = item.path("day").asText("");
          if (dayName.equalsIgnoreCase(frenchDay) || dayName.equalsIgnoreCase(dayOfWeek.name())) {
            boolean open = item.path("open").asBoolean(true);
            if (!open) {
              return new DaySchedule(false, null, null);
            }
            LocalTime start = parseLocalTime(item.path("start").asText("09:00"));
            LocalTime end = parseLocalTime(item.path("end").asText("19:00"));
            return new DaySchedule(true, start, end);
          }
        }
      } catch (Exception e) {
        log.warn("Erreur lors du parsing JSON des horaires: {}", e.getMessage());
      }
    }

    if (dayOfWeek == DayOfWeek.SUNDAY) {
      return new DaySchedule(false, null, null);
    }
    return new DaySchedule(true, LocalTime.of(9, 0), LocalTime.of(19, 0));
  }

  private LocalTime parseLocalTime(String timeStr) {
    try {
      return LocalTime.parse(timeStr.trim(), DateTimeFormatter.ofPattern("HH:mm"));
    } catch (Exception e) {
      return LocalTime.of(9, 0);
    }
  }

  private String getFrenchDayName(DayOfWeek dayOfWeek) {
    return switch (dayOfWeek) {
      case MONDAY -> "Lundi";
      case TUESDAY -> "Mardi";
      case WEDNESDAY -> "Mercredi";
      case THURSDAY -> "Jeudi";
      case FRIDAY -> "Vendredi";
      case SATURDAY -> "Samedi";
      case SUNDAY -> "Dimanche";
    };
  }

  private static class DaySchedule {
    private final boolean open;
    private final LocalTime openingTime;
    private final LocalTime closingTime;

    public DaySchedule(boolean open, LocalTime openingTime, LocalTime closingTime) {
      this.open = open;
      this.openingTime = openingTime;
      this.closingTime = closingTime;
    }

    public boolean isOpen() {
      return open;
    }

    public LocalTime getOpeningTime() {
      return openingTime;
    }

    public LocalTime getClosingTime() {
      return closingTime;
    }
  }
}
