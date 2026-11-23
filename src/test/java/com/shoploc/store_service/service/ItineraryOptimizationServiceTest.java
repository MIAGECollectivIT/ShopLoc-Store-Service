package com.shoploc.store_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.shoploc.store_service.Store;
import com.shoploc.store_service.StoreRepository;
import com.shoploc.store_service.StoreStatus;
import com.shoploc.store_service.dto.itinerary.OptimizeItineraryRequest;
import com.shoploc.store_service.dto.itinerary.OptimizeItineraryResponse;
import com.shoploc.store_service.dto.itinerary.TransportMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItineraryOptimizationServiceTest {

  @Mock private StoreRepository storeRepository;
  @Mock private GeocodingService geocodingService;

  private StoreScheduleEvaluator storeScheduleEvaluator;
  private GoogleMapsUrlBuilder googleMapsUrlBuilder;
  private ItineraryOptimizationService service;

  private static final ZoneId PARIS_ZONE = ZoneId.of("Europe/Paris");

  @BeforeEach
  void setUp() {
    storeScheduleEvaluator = new StoreScheduleEvaluator();
    googleMapsUrlBuilder = new GoogleMapsUrlBuilder();
    service =
        new ItineraryOptimizationService(
            storeRepository, geocodingService, storeScheduleEvaluator, googleMapsUrlBuilder);
  }

  @Test
  @DisplayName("Calcul de distance urbaine avec coefficient 1.35 sur Haversine")
  void shouldApplyUrbanDetourFactor() {
    // Lille Grand Place (50.6369, 3.0633) to Gare Lille Flandres (50.6364, 3.0699)
    double lat1 = 50.6369;
    double lon1 = 3.0633;
    double lat2 = 50.6364;
    double lon2 = 3.0699;

    double haversine = service.calculateHaversineDistanceKm(lat1, lon1, lat2, lon2);
    double urban = service.calculateUrbanDistance(lat1, lon1, lat2, lon2);

    assertEquals(haversine * 1.35, urban, 0.0001);
    assertTrue(urban > haversine);
  }

  @Test
  @DisplayName("Dédoublonnage strict des storeUids en entrée")
  void shouldDeduplicateIncomingStoreUids() {
    UUID storeUid = UUID.randomUUID();
    Store store =
        new Store("Boutique Unique", "10 Rue Nationale", null, "Alim", "FR...", UUID.randomUUID());
    store.setUid(storeUid);
    store.setLatitude(50.6356);
    store.setLongitude(3.0617);
    store.setStatus(StoreStatus.ACTIVE);

    when(storeRepository.findByUidIn(List.of(storeUid))).thenReturn(List.of(store));

    // Request with duplicate UUIDs (multiple items from same store)
    OptimizeItineraryRequest request =
        new OptimizeItineraryRequest(
            List.of(storeUid, storeUid, storeUid),
            50.63,
            3.06,
            "Depart",
            TransportMode.DRIVING,
            Instant.now());

    OptimizeItineraryResponse response = service.optimizeItinerary(request);

    assertNotNull(response);
    assertEquals(
        1,
        response.orderedSteps().size(),
        "Un commerce présent plusieurs fois dans le panier ne doit apparaître qu'une seule fois");
    assertEquals(storeUid, response.orderedSteps().get(0).storeUid());
  }

  @Test
  @DisplayName("Exclusion stricte des commerces au statut REVOKED avec avertissement")
  void shouldExcludeRevokedStoresAndAddWarning() {
    UUID activeUid = UUID.randomUUID();
    UUID revokedUid = UUID.randomUUID();

    Store activeStore =
        new Store("Boutique Active", "Adresse 1", null, "Alim", "FR...", UUID.randomUUID());
    activeStore.setUid(activeUid);
    activeStore.setLatitude(50.631);
    activeStore.setLongitude(3.061);
    activeStore.setStatus(StoreStatus.ACTIVE);

    Store revokedStore =
        new Store("Boutique Révoquée", "Adresse 2", null, "Alim", "FR...", UUID.randomUUID());
    revokedStore.setUid(revokedUid);
    revokedStore.setLatitude(50.635);
    revokedStore.setLongitude(3.065);
    revokedStore.setStatus(StoreStatus.REVOKED);

    when(storeRepository.findByUidIn(anyList())).thenReturn(List.of(activeStore, revokedStore));

    OptimizeItineraryRequest request =
        new OptimizeItineraryRequest(
            List.of(activeUid, revokedUid),
            50.63,
            3.06,
            "Depart",
            TransportMode.DRIVING,
            Instant.now());

    OptimizeItineraryResponse response = service.optimizeItinerary(request);

    assertEquals(
        1, response.orderedSteps().size(), "Seule la boutique active doit être dans les étapes");
    assertEquals(activeUid, response.orderedSteps().get(0).storeUid());
    assertTrue(response.warnings().stream().anyMatch(w -> w.contains("révoqué")));
  }

  @Test
  @DisplayName("Pénalité fermeture < 45m : le commerce fermant bientôt est visité en premier")
  void shouldPrioritizeStoreClosingSoonOverCloserStore() {
    // Departure Monday at 18:35
    LocalDate nextMonday = LocalDate.now(PARIS_ZONE);
    while (nextMonday.getDayOfWeek() != DayOfWeek.MONDAY) {
      nextMonday = nextMonday.plusDays(1);
    }
    Instant departureTime = nextMonday.atTime(18, 35).atZone(PARIS_ZONE).toInstant();

    // Store A is closer (1 km away) but closes at 22:00
    UUID uidA = UUID.randomUUID();
    Store storeA =
        new Store("Store A (Tardif)", "Addr A", null, "Alim", "FR...", UUID.randomUUID());
    storeA.setUid(uidA);
    storeA.setLatitude(50.632);
    storeA.setLongitude(3.061);
    storeA.setOpeningHours(
        "[{\"day\":\"Lundi\",\"open\":true,\"start\":\"09:00\",\"end\":\"22:00\"}]");

    // Store B is slightly further (1.5 km away) but closes at 19:00 (critical: in 25 min!)
    UUID uidB = UUID.randomUUID();
    Store storeB =
        new Store("Store B (Ferme Bientôt)", "Addr B", null, "Alim", "FR...", UUID.randomUUID());
    storeB.setUid(uidB);
    storeB.setLatitude(50.639);
    storeB.setLongitude(3.067);
    storeB.setOpeningHours(
        "[{\"day\":\"Lundi\",\"open\":true,\"start\":\"09:00\",\"end\":\"19:00\"}]");

    when(storeRepository.findByUidIn(anyList())).thenReturn(List.of(storeA, storeB));

    OptimizeItineraryRequest request =
        new OptimizeItineraryRequest(
            List.of(uidA, uidB), 50.630, 3.060, "Depart", TransportMode.DRIVING, departureTime);

    OptimizeItineraryResponse response = service.optimizeItinerary(request);

    assertEquals(2, response.orderedSteps().size());
    // Store B must be scheduled FIRST to avoid closing gate!
    assertEquals(
        uidB,
        response.orderedSteps().get(0).storeUid(),
        "Store B fermant dans 25 min doit être priorisé");
    assertEquals(uidA, response.orderedSteps().get(1).storeUid());
  }

  @Test
  @DisplayName("Détection de deux commerces colocalisés à la même adresse")
  void shouldFlagColocalizedStores() {
    UUID uid1 = UUID.randomUUID();
    UUID uid2 = UUID.randomUUID();

    Store store1 =
        new Store("Stall 1", "Halle de Wazemmes", null, "Alim", "FR...", UUID.randomUUID());
    store1.setUid(uid1);
    store1.setLatitude(50.6280);
    store1.setLongitude(3.0500);

    Store store2 =
        new Store("Stall 2", "Halle de Wazemmes", null, "Alim", "FR...", UUID.randomUUID());
    store2.setUid(uid2);
    store2.setLatitude(50.6280);
    store2.setLongitude(3.0500);

    when(storeRepository.findByUidIn(anyList())).thenReturn(List.of(store1, store2));

    OptimizeItineraryRequest request =
        new OptimizeItineraryRequest(
            List.of(uid1, uid2), 50.6280, 3.0500, "Depart", TransportMode.WALKING, Instant.now());

    OptimizeItineraryResponse response = service.optimizeItinerary(request);

    assertEquals(2, response.orderedSteps().size());
    assertTrue(
        response.orderedSteps().get(1).isSameLocationAsPrevious(),
        "La 2e boutique à même adresse doit être marquée colocalisée");
    assertEquals(0.0, response.orderedSteps().get(1).distanceFromPreviousKm());
    assertEquals(0, response.orderedSteps().get(1).travelTimeMinutes());
  }
}
