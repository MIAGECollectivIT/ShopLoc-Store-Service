package com.shoploc.store_service;

import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.dto.StoreResponse;
import com.shoploc.store_service.exception.AccessDeniedException;
import com.shoploc.store_service.security.RequiresRole;
import com.shoploc.store_service.storage.ImageResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/stores")
@Tag(name = "Stores", description = "Gestion des commerces")
public class StoreController {

  private final StoreService storeService;

  public StoreController(StoreService storeService) {
    this.storeService = storeService;
  }

  @GetMapping
  @Operation(summary = "Récupère tous les commerces (accès public)")
  public List<StoreResponse> getAllStores(@RequestParam(required = false) Long userId) {
    List<Store> stores;
    if (userId != null) {
      stores = storeService.getStoresByUserId(userId);
    } else {
      stores = storeService.getAllStores();
    }

    List<StoreResponse> responseList = new ArrayList<>();
    for (Store store : stores) {
      responseList.add(StoreResponse.fromEntity(store));
    }
    return responseList;
  }

  @GetMapping("/{identifier}")
  @Operation(
      summary = "Récupère un commerce par son identifiant numérique ou son uid UUID (accès public)")
  public ResponseEntity<StoreResponse> getStoreById(@PathVariable String identifier) {
    Optional<Store> storeOptional = resolveStore(identifier);
    if (storeOptional.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    Store store = storeOptional.get();
    StoreResponse response = StoreResponse.fromEntity(store);
    return ResponseEntity.ok(response);
  }

  public ResponseEntity<StoreResponse> getStoreById(Long storeId) {
    return getStoreById(String.valueOf(storeId));
  }

  @GetMapping("/{identifier}/image")
  @Operation(
      summary =
          "Récupérer l'image binaire de la devanture d'un commerce hébergée sur S3 (avec Cache-Control - Accès public)")
  public ResponseEntity<byte[]> getStoreImage(@PathVariable String identifier) {
    ImageResource resource = storeService.getStoreImage(identifier);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(resource.getContentType()))
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
        .body(resource.getBytes());
  }

  public ResponseEntity<byte[]> getStoreImage(Long storeId) {
    return getStoreImage(String.valueOf(storeId));
  }

  private Optional<Store> resolveStore(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      return Optional.empty();
    }
    String trimmed = identifier.trim();
    try {
      java.util.UUID uid = java.util.UUID.fromString(trimmed);
      return storeService.getStoreByUid(uid);
    } catch (IllegalArgumentException e) {
      try {
        Long id = Long.parseLong(trimmed);
        return storeService.getStoreById(id);
      } catch (NumberFormatException nfe) {
        return Optional.empty();
      }
    }
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresRole({"ADMIN", "MERCHANT"})
  @Operation(
      summary =
          "Créer un nouveau commerce avec upload d'image (multipart/form-data) - Réservé aux administrateurs et commerçants")
  public ResponseEntity<StoreResponse> createStore(
      @Valid @RequestPart("store") StoreCreateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image,
      HttpServletRequest servletRequest) {
    resolveAndValidateUserId(request, servletRequest);
    Store createdStore = storeService.createStore(request, image);
    StoreResponse response = StoreResponse.fromEntity(createdStore);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @RequiresRole({"ADMIN", "MERCHANT"})
  @Operation(
      summary =
          "Créer un nouveau commerce sans image ou avec URL directe (application/json) - Réservé aux administrateurs et commerçants")
  public ResponseEntity<StoreResponse> createStore(
      @Valid @RequestBody StoreCreateRequest request, HttpServletRequest servletRequest) {
    resolveAndValidateUserId(request, servletRequest);
    Store createdStore = storeService.createStore(request);
    StoreResponse response = StoreResponse.fromEntity(createdStore);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  private void resolveAndValidateUserId(
      StoreCreateRequest request, HttpServletRequest servletRequest) {
    String role =
        servletRequest != null
            ? (String) servletRequest.getAttribute("authenticatedUserRole")
            : null;
    Object dbIdObj =
        servletRequest != null ? servletRequest.getAttribute("authenticatedUserDbId") : null;
    Long authenticatedDbId = null;
    if (dbIdObj instanceof Long l) {
      authenticatedDbId = l;
    } else if (dbIdObj instanceof Number n) {
      authenticatedDbId = n.longValue();
    }

    if ("MERCHANT".equalsIgnoreCase(role)) {
      // A merchant can only create a store for their own account (IDOR prevention)
      if (authenticatedDbId == null || authenticatedDbId <= 0) {
        throw new AccessDeniedException(
            "Identifiant commerçant introuvable dans le jeton d'authentification");
      }
      if (request.getUserId() != null && !request.getUserId().equals(authenticatedDbId)) {
        throw new AccessDeniedException(
            "Accès refusé : un commerçant ne peut créer un commerce que pour son propre compte (id "
                + authenticatedDbId
                + ")");
      }
      request.setUserId(authenticatedDbId);
    } else {
      // Administrator or test/internal context without servletRequest session
      if (request.getUserId() == null || request.getUserId() <= 0) {
        request.setUserId(authenticatedDbId);
      }
    }

    if (request.getUserId() == null || request.getUserId() <= 0) {
      throw new IllegalArgumentException(
          "L'identifiant du partenaire propriétaire (userId) est obligatoire et doit être un entier valide.");
    }
  }
}
