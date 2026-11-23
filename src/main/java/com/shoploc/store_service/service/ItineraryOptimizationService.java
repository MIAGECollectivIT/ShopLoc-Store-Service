package com.shoploc.store_service.service;

import com.shoploc.store_service.Store;
import com.shoploc.store_service.StoreRepository;
import com.shoploc.store_service.StoreStatus;
import com.shoploc.store_service.dto.itinerary.ItineraryOriginResponse;
import com.shoploc.store_service.dto.itinerary.ItineraryStepResponse;
import com.shoploc.store_service.dto.itinerary.OptimizeItineraryRequest;
import com.shoploc.store_service.dto.itinerary.OptimizeItineraryResponse;
import com.shoploc.store_service.dto.itinerary.TransportMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Core engine for Click & Collect itinerary optimization (SLE-31, SLE-32, SLE-297, SLE-294).
 */
@Service
public class ItineraryOptimizationService {

  private static final Logger log = LoggerFactory.getLogger(ItineraryOptimizationService.class);
  public static final double URBAN_DETOUR_FACTOR = 1.35;
  private static final double EARTH_RADIUS_KM = 6371.0;
  private static final double CLOSING_PENALTY_WEIGHT = 50.0;
  private static final double CLOSED_STORE_PENALTY = 1000.0;

  private final StoreRepository storeRepository;
  private final GeocodingService geocodingService;
  private final StoreScheduleEvaluator storeScheduleEvaluator;
  private final GoogleMapsUrlBuilder googleMapsUrlBuilder;

  @Autowired
  public ItineraryOptimizationService(
      StoreRepository storeRepository,
      GeocodingService geocodingService,
      StoreScheduleEvaluator storeScheduleEvaluator,
      GoogleMapsUrlBuilder googleMapsUrlBuilder) {
    this.storeRepository = storeRepository;
    this.geocodingService = geocodingService;
    this.storeScheduleEvaluator = storeScheduleEvaluator;
    this.googleMapsUrlBuilder = googleMapsUrlBuilder;
  }

  public OptimizeItineraryResponse optimizeItinerary(OptimizeItineraryRequest request) {
    if (request == null || request.storeUids() == null || request.storeUids().isEmpty()) {
      throw new IllegalArgumentException("La liste des boutiques partenaires ne peut pas être vide");
    }

    List<String> warnings = new ArrayList<>();

    // 1. Deduplicate incoming store UUIDs
    List<UUID> uniqueUids =
        request.storeUids().stream().filter(Objects::nonNull).distinct().toList();

    // 2. Retrieve stores from database
    List<Store> fetchedStores = storeRepository.findByUidIn(uniqueUids);
    Map<UUID, Store> storeMap = new HashMap<>();
    for (Store s : fetchedStores) {
      storeMap.put(s.getUid(), s);
    }

    // 3. Filter out REVOKED stores and non-existent stores
    List<Store> activeStores = new ArrayList<>();
    for (UUID uid : uniqueUids) {
      Store store = storeMap.get(uid);
      if (store == null) {
        warnings.add("La boutique avec l'identifiant " + uid + " est introuvable.");
      } else if (store.getStatus() == StoreStatus.REVOKED) {
        warnings.add("Le commerce '" + store.getName() + "' a été révoqué par l'administration et ne peut être inclus dans la tournée de retrait.");
      } else {
        activeStores.add(store);
      }
    }

    if (activeStores.isEmpty()) {
      throw new IllegalArgumentException("Aucun commerce actif n'a pu être sélectionné pour la tournée.");
    }

    // 4. Parallel self-healing geocoding if coordinates are missing
    List<Store> storesWithoutCoords =
        activeStores.stream()
            .filter(s -> s.getLatitude() == null || s.getLongitude() == null)
            .toList();

    if (!storesWithoutCoords.isEmpty()) {
      geocodingService.geocodeStoresInParallel(storesWithoutCoords);
    }

    // 5. Determine Origin
    TransportMode transportMode =
        request.transportMode() != null ? request.transportMode() : TransportMode.DRIVING;
    Instant departureTime =
        request.departureTime() != null ? request.departureTime() : Instant.now();

    ItineraryOriginResponse originResponse = resolveOrigin(request);

    // 6. TSP Optimization using cost function (Urban Detour + Closing Penalty < 45m)
    List<Store> orderedStores = orderStoresOptimally(originResponse, activeStores, departureTime, transportMode);

    // 7. Build detailed steps chronologically
    List<ItineraryStepResponse> orderedSteps = new ArrayList<>();
    Instant currentInstant = departureTime;
    double cumulativeDistance = 0.0;
    int cumulativeDuration = 0;

    Double prevLat = originResponse.latitude();
    Double prevLng = originResponse.longitude();
    String prevAddress = originResponse.address();

    for (int i = 0; i < orderedStores.size(); i++) {
      Store store = orderedStores.get(i);
      boolean isSameLocation =
          (i > 0 && areLocationsEqual(prevLat, prevLng, prevAddress, store.getLatitude(), store.getLongitude(), store.getAddress()));

      double stepDistance = isSameLocation ? 0.0 : calculateUrbanDistance(prevLat, prevLng, store.getLatitude(), store.getLongitude());
      int travelMinutes = isSameLocation ? 0 : calculateTravelMinutes(stepDistance, transportMode);

      Instant arrivalTime = currentInstant.plus(Duration.ofMinutes(travelMinutes));

      StoreScheduleEvaluator.ScheduleEvaluation scheduleEval =
          storeScheduleEvaluator.evaluateStoreAtTime(store, arrivalTime);

      if (scheduleEval.warningAlert() != null) {
        warnings.add(store.getName() + " : " + scheduleEval.warningAlert());
      }

      int stepNumber = i + 1;
      orderedSteps.add(
          new ItineraryStepResponse(
              stepNumber,
              store.getUid(),
              store.getName(),
              store.getAddress(),
              store.getLatitude(),
              store.getLongitude(),
              roundToTwoDecimals(stepDistance),
              travelMinutes,
              arrivalTime,
              scheduleEval.isOpenAtArrival(),
              scheduleEval.isClosedAllDay(),
              isSameLocation,
              scheduleEval.scheduleInfo(),
              scheduleEval.warningAlert()));

      cumulativeDistance += stepDistance;
      cumulativeDuration += travelMinutes + transportMode.getStopHandlingMinutes();
      currentInstant = arrivalTime.plus(Duration.ofMinutes(transportMode.getStopHandlingMinutes()));

      prevLat = store.getLatitude();
      prevLng = store.getLongitude();
      prevAddress = store.getAddress();
    }

    ItineraryStepResponse destinationStep = orderedSteps.get(orderedSteps.size() - 1);

    // 8. Generate Google Maps URL
    GoogleMapsUrlBuilder.UrlBuildResult urlResult =
        googleMapsUrlBuilder.buildDirectionsUrl(originResponse, orderedSteps, transportMode);

    return new OptimizeItineraryResponse(
        originResponse,
        destinationStep,
        orderedSteps,
        roundToTwoDecimals(cumulativeDistance),
        cumulativeDuration,
        transportMode,
        urlResult.url(),
        urlResult.waypointsTruncated(),
        warnings);
  }

  private ItineraryOriginResponse resolveOrigin(OptimizeItineraryRequest request) {
    if (request.originLatitude() != null && request.originLongitude() != null) {
      String address =
          request.originAddress() != null && !request.originAddress().isBlank()
              ? request.originAddress().trim()
              : "Position GPS";
      return new ItineraryOriginResponse(address, request.originLatitude(), request.originLongitude(), true);
    }

    if (request.originAddress() != null && !request.originAddress().isBlank()) {
      Optional<GeocodingService.Coordinates> coordsOpt =
          geocodingService.tryGeocode(request.originAddress().trim());
      if (coordsOpt.isPresent()) {
        return new ItineraryOriginResponse(
            request.originAddress().trim(),
            coordsOpt.get().latitude(),
            coordsOpt.get().longitude(),
            true);
      }
      return new ItineraryOriginResponse(request.originAddress().trim(), null, null, true);
    }

    return new ItineraryOriginResponse("Point de départ", null, null, false);
  }

  private List<Store> orderStoresOptimally(
      ItineraryOriginResponse origin,
      List<Store> stores,
      Instant departureTime,
      TransportMode transportMode) {

    if (stores.size() <= 1) {
      return new ArrayList<>(stores);
    }

    // For N <= 8, solve with optimal permutations
    if (stores.size() <= 8) {
      return findBestPermutation(origin, stores, departureTime, transportMode);
    }

    // For N > 8, greedy Nearest Neighbor with cost function
    return greedyNearestNeighborWithPenalty(origin, stores, departureTime, transportMode);
  }

  private List<Store> findBestPermutation(
      ItineraryOriginResponse origin,
      List<Store> stores,
      Instant departureTime,
      TransportMode transportMode) {

    List<List<Store>> allPermutations = new ArrayList<>();
    generatePermutations(stores.size(), new ArrayList<>(stores), allPermutations);

    List<Store> bestOrder = stores;
    double minCost = Double.MAX_VALUE;

    for (List<Store> perm : allPermutations) {
      double cost = evaluatePermutationCost(origin, perm, departureTime, transportMode);
      if (cost < minCost) {
        minCost = cost;
        bestOrder = perm;
      }
    }
    return bestOrder;
  }

  private void generatePermutations(int n, List<Store> elements, List<List<Store>> result) {
    if (n == 1) {
      result.add(new ArrayList<>(elements));
    } else {
      for (int i = 0; i < n - 1; i++) {
        generatePermutations(n - 1, elements, result);
        if (n % 2 == 0) {
          Collections.swap(elements, i, n - 1);
        } else {
          Collections.swap(elements, 0, n - 1);
        }
      }
      generatePermutations(n - 1, elements, result);
    }
  }

  private double evaluatePermutationCost(
      ItineraryOriginResponse origin,
      List<Store> perm,
      Instant departureTime,
      TransportMode transportMode) {

    double totalCost = 0.0;
    Instant currentInstant = departureTime;
    Double prevLat = origin.latitude();
    Double prevLng = origin.longitude();

    for (Store store : perm) {
      double dist = calculateUrbanDistance(prevLat, prevLng, store.getLatitude(), store.getLongitude());
      int travelMinutes = calculateTravelMinutes(dist, transportMode);
      Instant arrival = currentInstant.plus(Duration.ofMinutes(travelMinutes));

      StoreScheduleEvaluator.ScheduleEvaluation eval =
          storeScheduleEvaluator.evaluateStoreAtTime(store, arrival);

      double penalty = calculateClosingPenalty(eval);
      totalCost += dist + penalty;

      currentInstant = arrival.plus(Duration.ofMinutes(transportMode.getStopHandlingMinutes()));
      prevLat = store.getLatitude();
      prevLng = store.getLongitude();
    }

    return totalCost;
  }

  private List<Store> greedyNearestNeighborWithPenalty(
      ItineraryOriginResponse origin,
      List<Store> stores,
      Instant departureTime,
      TransportMode transportMode) {

    List<Store> remaining = new ArrayList<>(stores);
    List<Store> ordered = new ArrayList<>();
    Instant currentInstant = departureTime;
    Double currentLat = origin.latitude();
    Double currentLng = origin.longitude();

    while (!remaining.isEmpty()) {
      Store nextStore = null;
      double minCost = Double.MAX_VALUE;

      for (Store candidate : remaining) {
        double dist = calculateUrbanDistance(currentLat, currentLng, candidate.getLatitude(), candidate.getLongitude());
        int travelMinutes = calculateTravelMinutes(dist, transportMode);
        Instant arrival = currentInstant.plus(Duration.ofMinutes(travelMinutes));

        StoreScheduleEvaluator.ScheduleEvaluation eval =
            storeScheduleEvaluator.evaluateStoreAtTime(candidate, arrival);

        double penalty = calculateClosingPenalty(eval);
        double cost = dist + penalty;

        if (cost < minCost) {
          minCost = cost;
          nextStore = candidate;
        }
      }

      if (nextStore == null) {
        nextStore = remaining.get(0);
      }

      ordered.add(nextStore);
      remaining.remove(nextStore);

      double dist = calculateUrbanDistance(currentLat, currentLng, nextStore.getLatitude(), nextStore.getLongitude());
      int travelMinutes = calculateTravelMinutes(dist, transportMode);
      currentInstant = currentInstant.plus(Duration.ofMinutes(travelMinutes + transportMode.getStopHandlingMinutes()));
      currentLat = nextStore.getLatitude();
      currentLng = nextStore.getLongitude();
    }

    return ordered;
  }

  private double calculateClosingPenalty(StoreScheduleEvaluator.ScheduleEvaluation eval) {
    if (eval.isClosedAllDay()) {
      return CLOSED_STORE_PENALTY;
    }
    if (!eval.isOpenAtArrival()) {
      return CLOSED_STORE_PENALTY / 2.0;
    }
    if (eval.minutesUntilClosing() < 45 && eval.minutesUntilClosing() >= 0) {
      double ratio = (45.0 - eval.minutesUntilClosing()) / 45.0;
      return CLOSING_PENALTY_WEIGHT * Math.pow(ratio, 2);
    }
    return 0.0;
  }

  public double calculateUrbanDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
    if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
      return 1.0; // Default nominal 1 km if coordinates unavailable
    }
    double haversine = calculateHaversineDistanceKm(lat1, lon1, lat2, lon2);
    return haversine * URBAN_DETOUR_FACTOR;
  }

  public double calculateHaversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);

    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);

    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return EARTH_RADIUS_KM * c;
  }

  public int calculateTravelMinutes(double distanceKm, TransportMode transportMode) {
    double speedKmH = transportMode.getAverageSpeedKmH();
    double hours = distanceKm / speedKmH;
    int minutes = (int) Math.round(hours * 60.0);
    return Math.max(1, minutes);
  }

  private boolean areLocationsEqual(
      Double lat1, Double lng1, String addr1, Double lat2, Double lng2, String addr2) {
    if (lat1 != null && lng1 != null && lat2 != null && lng2 != null) {
      return Math.abs(lat1 - lat2) < 0.0001 && Math.abs(lng1 - lng2) < 0.0001;
    }
    if (addr1 != null && addr2 != null) {
      return addr1.trim().equalsIgnoreCase(addr2.trim());
    }
    return false;
  }

  private double roundToTwoDecimals(double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }
}
