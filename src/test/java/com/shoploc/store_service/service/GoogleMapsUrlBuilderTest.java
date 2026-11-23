package com.shoploc.store_service.service;

import static org.junit.jupiter.api.Assertions.*;

import com.shoploc.store_service.dto.itinerary.ItineraryOriginResponse;
import com.shoploc.store_service.dto.itinerary.ItineraryStepResponse;
import com.shoploc.store_service.dto.itinerary.TransportMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleMapsUrlBuilderTest {

  private GoogleMapsUrlBuilder urlBuilder;

  @BeforeEach
  void setUp() {
    urlBuilder = new GoogleMapsUrlBuilder();
  }

  @Test
  @DisplayName("1 seul commerce: l'URL ne doit PAS contenir de paramètre waypoints=")
  void shouldNotIncludeWaypointsForSingleStore() {
    ItineraryOriginResponse origin =
        new ItineraryOriginResponse("Grand Place, Lille", 50.629250, 3.057256, true);

    ItineraryStepResponse step1 =
        new ItineraryStepResponse(
            1,
            UUID.randomUUID(),
            "Burger King Lille",
            "10 Rue Nationale, 59000 Lille",
            50.635600,
            3.061700,
            1.2,
            4,
            Instant.now(),
            true,
            false,
            false,
            "09:00 - 19:00",
            null);

    GoogleMapsUrlBuilder.UrlBuildResult result =
        urlBuilder.buildDirectionsUrl(origin, List.of(step1), TransportMode.DRIVING);

    assertNotNull(result.url());
    assertFalse(result.url().contains("waypoints="), "L'URL ne doit pas avoir de paramètre waypoints");
    assertTrue(result.url().contains("origin=50.629250,3.057256"));
    assertTrue(result.url().contains("destination=50.635600,3.061700"));
    assertTrue(result.url().contains("&travelmode=driving"));
    assertFalse(result.waypointsTruncated());
  }

  @Test
  @DisplayName("Plusieurs commerces: waypoints séparés par %7C et compact lat,lng")
  void shouldIncludeWaypointsForMultipleStores() {
    ItineraryOriginResponse origin =
        new ItineraryOriginResponse("Gare Lille", 50.636000, 3.070000, true);

    ItineraryStepResponse step1 =
        new ItineraryStepResponse(
            1,
            UUID.randomUUID(),
            "Magasin 1",
            "Adresse 1",
            50.631000,
            3.062000,
            1.0,
            3,
            Instant.now(),
            true,
            false,
            false,
            "09:00 - 19:00",
            null);

    ItineraryStepResponse step2 =
        new ItineraryStepResponse(
            2,
            UUID.randomUUID(),
            "Magasin 2",
            "Adresse 2",
            50.634000,
            3.065000,
            1.5,
            5,
            Instant.now(),
            true,
            false,
            false,
            "09:00 - 19:00",
            null);

    GoogleMapsUrlBuilder.UrlBuildResult result =
        urlBuilder.buildDirectionsUrl(origin, List.of(step1, step2), TransportMode.BICYCLE);

    assertTrue(result.url().contains("origin=50.636000,3.070000"));
    assertTrue(result.url().contains("destination=50.634000,3.065000"));
    assertTrue(result.url().contains("waypoints=50.631000,3.062000"));
    assertTrue(result.url().contains("&travelmode=bicycling"));
  }

  @Test
  @DisplayName("Fallback textuel avec encodage strict UTF-8 et '+' pour les espaces")
  void shouldFallbackToTextualAddressWithPlus() {
    ItineraryOriginResponse origin =
        new ItineraryOriginResponse("Gare Lille Flandres", null, null, true);

    ItineraryStepResponse step1 =
        new ItineraryStepResponse(
            1,
            UUID.randomUUID(),
            "Boutique Sans GPS",
            "10 Rue Nationale, 59000 Lille",
            null,
            null,
            1.0,
            3,
            Instant.now(),
            true,
            false,
            false,
            "09:00 - 19:00",
            null);

    GoogleMapsUrlBuilder.UrlBuildResult result =
        urlBuilder.buildDirectionsUrl(origin, List.of(step1), TransportMode.WALKING);

    assertTrue(result.url().contains("origin=Gare+Lille+Flandres"));
    assertTrue(result.url().contains("destination=10+Rue+Nationale%2C+59000+Lille"));
    assertTrue(result.url().contains("&travelmode=walking"));
  }

  @Test
  @DisplayName("Plafond strict de 9 waypoints intermédiaires")
  void shouldCapWaypointsAtNine() {
    ItineraryOriginResponse origin =
        new ItineraryOriginResponse("Depart", 50.0, 3.0, true);

    List<ItineraryStepResponse> steps = new ArrayList<>();
    for (int i = 1; i <= 15; i++) {
      steps.add(
          new ItineraryStepResponse(
              i,
              UUID.randomUUID(),
              "Magasin " + i,
              "Adresse " + i,
              50.0 + (i * 0.01),
              3.0 + (i * 0.01),
              0.5,
              2,
              Instant.now(),
              true,
              false,
              false,
              "09:00 - 19:00",
              null));
    }

    GoogleMapsUrlBuilder.UrlBuildResult result =
        urlBuilder.buildDirectionsUrl(origin, steps, TransportMode.DRIVING);

    assertTrue(result.waypointsTruncated());
    String waypointsParam = result.url().split("&waypoints=")[1].split("&travelmode=")[0];
    String[] parts = waypointsParam.split("%7C");
    assertEquals(9, parts.length, "Le nombre de waypoints doit être exactement plafonné à 9");
  }

  @Test
  @DisplayName("Dédoublonnage des waypoints consécutifs identiques (colocalisés)")
  void shouldDeduplicateConsecutiveIdenticalWaypoints() {
    ItineraryOriginResponse origin =
        new ItineraryOriginResponse("Depart", 50.0, 3.0, true);

    ItineraryStepResponse step1 =
        new ItineraryStepResponse(
            1, UUID.randomUUID(), "Shop A", "Same Place", 50.1, 3.1, 1.0, 2, Instant.now(), true, false, false, "09:00-19:00", null);

    ItineraryStepResponse step2 =
        new ItineraryStepResponse(
            2, UUID.randomUUID(), "Shop B", "Same Place", 50.1, 3.1, 0.0, 0, Instant.now(), true, false, true, "09:00-19:00", null);

    ItineraryStepResponse step3 =
        new ItineraryStepResponse(
            3, UUID.randomUUID(), "Shop C", "Other Place", 50.2, 3.2, 2.0, 5, Instant.now(), true, false, false, "09:00-19:00", null);

    GoogleMapsUrlBuilder.UrlBuildResult result =
        urlBuilder.buildDirectionsUrl(origin, List.of(step1, step2, step3), TransportMode.DRIVING);

    String waypointsParam = result.url().split("&waypoints=")[1].split("&travelmode=")[0];
    String[] parts = waypointsParam.split("%7C");
    assertEquals(1, parts.length, "Les étapes consécutives à même localisation ne doivent pas être dupliquées dans waypoints");
  }
}
