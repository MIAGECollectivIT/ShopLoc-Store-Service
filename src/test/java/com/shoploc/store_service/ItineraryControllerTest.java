package com.shoploc.store_service;

import static org.junit.jupiter.api.Assertions.*;

import com.shoploc.store_service.client.UserClient;
import com.shoploc.store_service.dto.itinerary.OptimizeItineraryRequest;
import com.shoploc.store_service.dto.itinerary.OptimizeItineraryResponse;
import com.shoploc.store_service.dto.itinerary.TransportMode;
import com.shoploc.store_service.storage.ImageStorageService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ItineraryControllerTest {

  @Autowired private ItineraryController itineraryController;
  @Autowired private StoreRepository storeRepository;

  @MockitoBean private UserClient userClient;
  @MockitoBean private ImageStorageService imageStorageService;

  @Test
  @DisplayName("POST /v1/stores/itinerary/optimize - Calcul réussi pour un commerce")
  void shouldOptimizeItinerarySuccessfully() {
    Store store =
        new Store(
            "Boulangerie Test",
            "14 Place Rihour, 59000 Lille",
            null,
            "Alimentation",
            "FR7630006000011234567890189",
            UUID.randomUUID());
    store.setLatitude(50.6360);
    store.setLongitude(3.0620);
    store.setStatus(StoreStatus.ACTIVE);
    store = storeRepository.save(store);

    OptimizeItineraryRequest request =
        new OptimizeItineraryRequest(
            List.of(store.getUid()),
            50.6300,
            3.0600,
            "Grand Place",
            TransportMode.DRIVING,
            Instant.now());

    ResponseEntity<OptimizeItineraryResponse> response =
        itineraryController.optimizeItinerary(request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().orderedSteps().size());
    assertNotNull(response.getBody().googleMapsUrl());
    assertFalse(
        response.getBody().googleMapsUrl().contains("waypoints="),
        "1 seule boutique ne doit pas avoir de waypoints");
  }
}
