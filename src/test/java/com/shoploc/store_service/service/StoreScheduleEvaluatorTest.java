package com.shoploc.store_service.service;

import static org.junit.jupiter.api.Assertions.*;

import com.shoploc.store_service.Store;
import com.shoploc.store_service.StoreAbsence;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoreScheduleEvaluatorTest {

  private StoreScheduleEvaluator evaluator;
  private static final ZoneId PARIS_ZONE = ZoneId.of("Europe/Paris");

  @BeforeEach
  void setUp() {
    evaluator = new StoreScheduleEvaluator();
  }

  @Test
  @DisplayName("Doit détecter une fermeture exceptionnelle d'absence")
  void shouldDetectAbsenceClosure() {
    Store store =
        new Store(
            "Test Store", "12 Rue de Lille", null, "Alimentation", "FR76...", UUID.randomUUID());

    LocalDate today = LocalDate.now(PARIS_ZONE);
    Instant start = today.minusDays(1).atStartOfDay(PARIS_ZONE).toInstant();
    Instant end = today.plusDays(2).atTime(23, 59, 59).atZone(PARIS_ZONE).toInstant();

    StoreAbsence absence = new StoreAbsence(store, start, end, "Inventaire annuel");
    store.setAbsences(List.of(absence));

    Instant arrival = today.atTime(14, 0).atZone(PARIS_ZONE).toInstant();

    StoreScheduleEvaluator.ScheduleEvaluation result =
        evaluator.evaluateStoreAtTime(store, arrival);

    assertFalse(result.isOpenAtArrival());
    assertTrue(result.isClosedAllDay());
    assertTrue(result.warningAlert().contains("Inventaire annuel"));
  }

  @Test
  @DisplayName("Doit détecter une fermeture le dimanche")
  void shouldDetectSundayClosure() {
    Store store =
        new Store(
            "Test Store", "12 Rue de Lille", null, "Alimentation", "FR76...", UUID.randomUUID());

    // Find next Sunday
    LocalDate sunday = LocalDate.now(PARIS_ZONE);
    while (sunday.getDayOfWeek() != DayOfWeek.SUNDAY) {
      sunday = sunday.plusDays(1);
    }

    Instant arrival = sunday.atTime(14, 0).atZone(PARIS_ZONE).toInstant();

    StoreScheduleEvaluator.ScheduleEvaluation result =
        evaluator.evaluateStoreAtTime(store, arrival);

    assertFalse(result.isOpenAtArrival());
    assertTrue(result.isClosedAllDay());
    assertTrue(result.warningAlert().contains("fermée toute la journée"));
  }

  @Test
  @DisplayName("Doit lever une alerte critique si fermeture dans moins de 45 minutes")
  void shouldAlertWhenClosingInLessThan45Minutes() {
    Store store =
        new Store(
            "Test Store", "12 Rue de Lille", null, "Alimentation", "FR76...", UUID.randomUUID());
    // Schedule: Mon-Sat 09:00 - 19:00
    LocalDate nextMonday = LocalDate.now(PARIS_ZONE);
    while (nextMonday.getDayOfWeek() != DayOfWeek.MONDAY) {
      nextMonday = nextMonday.plusDays(1);
    }

    // Arrival at 18:35 (25 minutes before 19:00)
    Instant arrival = nextMonday.atTime(18, 35).atZone(PARIS_ZONE).toInstant();

    StoreScheduleEvaluator.ScheduleEvaluation result =
        evaluator.evaluateStoreAtTime(store, arrival);

    assertTrue(result.isOpenAtArrival());
    assertFalse(result.isClosedAllDay());
    assertEquals(25, result.minutesUntilClosing());
    assertNotNull(result.warningAlert());
    assertTrue(
        result.warningAlert().contains("créneau critique"), "Doit signaler le créneau critique");
    assertTrue(result.warningAlert().contains("25 min"));
  }

  @Test
  @DisplayName("Pas d'alerte si fermeture dans plus de 45 minutes")
  void shouldNotAlertWhenClosingInMoreThan45Minutes() {
    Store store =
        new Store(
            "Test Store", "12 Rue de Lille", null, "Alimentation", "FR76...", UUID.randomUUID());

    LocalDate nextMonday = LocalDate.now(PARIS_ZONE);
    while (nextMonday.getDayOfWeek() != DayOfWeek.MONDAY) {
      nextMonday = nextMonday.plusDays(1);
    }

    // Arrival at 14:00 (5 hours before 19:00)
    Instant arrival = nextMonday.atTime(14, 0).atZone(PARIS_ZONE).toInstant();

    StoreScheduleEvaluator.ScheduleEvaluation result =
        evaluator.evaluateStoreAtTime(store, arrival);

    assertTrue(result.isOpenAtArrival());
    assertFalse(result.isClosedAllDay());
    assertNull(result.warningAlert());
  }
}
