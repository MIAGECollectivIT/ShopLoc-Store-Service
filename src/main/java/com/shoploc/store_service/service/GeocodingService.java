package com.shoploc.store_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoploc.store_service.entity.Store;
import com.shoploc.store_service.repository.StoreRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Self-healing geocoding service with thread-safe memory caching and parallel execution (SLE-31,
 * SLE-297).
 */
@Service
public class GeocodingService {

  private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
  private static final String BAN_API_URL = "https://api-adresse.data.gouv.fr/search/?q=";
  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private final StoreRepository storeRepository;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final Map<String, Optional<Coordinates>> cache = new ConcurrentHashMap<>();

  public record Coordinates(double latitude, double longitude) {}

  @Autowired
  public GeocodingService(StoreRepository storeRepository) {
    this(
        storeRepository,
        HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),
        new ObjectMapper());
  }

  public GeocodingService(
      StoreRepository storeRepository, HttpClient httpClient, ObjectMapper objectMapper) {
    this.storeRepository = storeRepository;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  /** Attempts to geocode a single address with memory cache lookup. */
  public Optional<Coordinates> tryGeocode(String address) {
    if (address == null || address.isBlank()) {
      return Optional.empty();
    }
    String normalizedKey = normalizeAddressKey(address);
    return cache.computeIfAbsent(normalizedKey, this::executeGeocodingLookup);
  }

  /**
   * Geocodes multiple stores lacking GPS coordinates in parallel with a strict 2-second global
   * timeout. Persists newly resolved coordinates to the database asynchronously.
   */
  public Map<UUID, Coordinates> geocodeStoresInParallel(List<Store> storesWithoutCoords) {
    if (storesWithoutCoords == null || storesWithoutCoords.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<UUID, CompletableFuture<Optional<Coordinates>>> futures = new HashMap<>();

    for (Store store : storesWithoutCoords) {
      if (store.getAddress() != null && !store.getAddress().isBlank()) {
        futures.put(
            store.getUid(), CompletableFuture.supplyAsync(() -> tryGeocode(store.getAddress())));
      }
    }

    if (futures.isEmpty()) {
      return Collections.emptyMap();
    }

    try {
      CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
          .get(2000, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      log.warn("Certains géocodages parallèles ont expiré ou échoué: {}", e.getMessage());
    }

    Map<UUID, Coordinates> resolved = new HashMap<>();
    List<Store> storesToUpdate = new ArrayList<>();

    for (Store store : storesWithoutCoords) {
      CompletableFuture<Optional<Coordinates>> future = futures.get(store.getUid());
      if (future != null && future.isDone() && !future.isCompletedExceptionally()) {
        try {
          Optional<Coordinates> coordsOpt = future.getNow(Optional.empty());
          if (coordsOpt.isPresent()) {
            Coordinates coords = coordsOpt.get();
            resolved.put(store.getUid(), coords);
            store.setLatitude(coords.latitude());
            store.setLongitude(coords.longitude());
            storesToUpdate.add(store);
          }
        } catch (Exception e) {
          log.warn(
              "Impossible d'extraire les coordonnées pour la boutique {}: {}",
              store.getUid(),
              e.getMessage());
        }
      }
    }

    if (!storesToUpdate.isEmpty()) {
      try {
        storeRepository.saveAll(storesToUpdate);
        log.info(
            "Coordonnées GPS auto-réparées et persistées pour {} boutiques.",
            storesToUpdate.size());
      } catch (Exception e) {
        log.warn("Erreur lors de la persistance des coordonnées auto-réparées: {}", e.getMessage());
      }
    }

    return resolved;
  }

  private Optional<Coordinates> executeGeocodingLookup(String normalizedKey) {
    try {
      String encoded = URLEncoder.encode(normalizedKey, StandardCharsets.UTF_8);
      URI uri = URI.create(BAN_API_URL + encoded + "&limit=1");

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(uri)
              .timeout(TIMEOUT)
              .header("Accept", "application/json")
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode features = root.path("features");
        if (features.isArray() && !features.isEmpty()) {
          JsonNode coordinates = features.get(0).path("geometry").path("coordinates");
          if (coordinates.isArray() && coordinates.size() >= 2) {
            // GeoJSON order is [longitude, latitude]
            double longitude = coordinates.get(0).asDouble();
            double latitude = coordinates.get(1).asDouble();
            return Optional.of(new Coordinates(latitude, longitude));
          }
        }
      }
    } catch (Exception e) {
      log.warn(
          "Erreur lors de l'appel au service de géocodage pour '{}': {}",
          normalizedKey,
          e.getMessage());
    }
    return Optional.empty();
  }

  private String normalizeAddressKey(String address) {
    return address.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  public void putInCache(String address, Coordinates coordinates) {
    if (address != null && coordinates != null) {
      cache.put(normalizeAddressKey(address), Optional.of(coordinates));
    }
  }

  public void clearCache() {
    cache.clear();
  }
}
