package com.shoploc.store_service;

import com.shoploc.store_service.dto.StoreAbsenceRequest;
import com.shoploc.store_service.dto.StoreAbsenceResponse;
import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.dto.StorePatchRequest;
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
import java.util.UUID;
import java.util.stream.Collectors;
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
  @Operation(summary = "Récupère tous les commerces (accès public - filtré sur actifs par défaut)")
  public List<StoreResponse> getAllStores(
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false, defaultValue = "false") boolean includeRevoked) {
    List<Store> stores;
    if (userId != null) {
      stores = storeService.getStoresByUserId(userId);
    } else if (includeRevoked) {
      stores = storeService.getAllStores(true);
    } else {
      stores = storeService.getAllStores();
    }

    List<StoreResponse> responseList = new ArrayList<>();
    for (Store store : stores) {
      responseList.add(StoreResponse.fromEntity(store));
    }
    return responseList;
  }

  public List<StoreResponse> getAllStores(Long userId) {
    return getAllStores(userId, false);
  }

  @GetMapping("/me")
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary = "Récupère les commerces du commerçant authentifié via son token JWT (anti-IDOR)")
  public List<StoreResponse> getMyStores(HttpServletRequest servletRequest) {
    Long authenticatedDbId = extractAuthenticatedUserId(servletRequest);
    if (authenticatedDbId == null || authenticatedDbId <= 0) {
      throw new AccessDeniedException(
          "Identifiant commerçant introuvable dans le jeton d'authentification");
    }
    List<Store> stores = storeService.getStoresByUserId(authenticatedDbId);
    List<StoreResponse> responseList = new ArrayList<>();
    for (Store store : stores) {
      responseList.add(StoreResponse.fromEntity(store));
    }
    return responseList;
  }

  @GetMapping("/search")
  @Operation(
      summary = "Recherche et filtrage multi-critères des commerces partenaires (accès public)")
  public List<StoreResponse> searchStores(
      @RequestParam(name = "name", required = false) String name,
      @RequestParam(name = "query", required = false) String query,
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "category", required = false) List<String> category,
      @RequestParam(name = "categories", required = false) List<String> categories) {
    String effectiveQuery = resolveSearchQuery(name, query, q);
    List<String> combinedCategories = combineCategories(category, categories);

    List<Store> stores = storeService.searchStores(effectiveQuery, combinedCategories);
    List<StoreResponse> responseList = new ArrayList<>();
    for (Store store : stores) {
      responseList.add(StoreResponse.fromEntity(store));
    }
    return responseList;
  }

  private String resolveSearchQuery(String name, String query, String q) {
    if (name != null && !name.isBlank()) {
      return name.trim();
    }
    if (query != null && !query.isBlank()) {
      return query.trim();
    }
    if (q != null && !q.isBlank()) {
      return q.trim();
    }
    return null;
  }

  private List<String> combineCategories(List<String> category, List<String> categories) {
    List<String> combined = new ArrayList<>();
    if (category != null) {
      combined.addAll(category);
    }
    if (categories != null) {
      combined.addAll(categories);
    }
    return combined;
  }

  @GetMapping("/{identifier}")
  @Operation(
      summary = "Récupère un commerce par son uid UUID ou son id numérique de repli (accès public)")
  public ResponseEntity<StoreResponse> getStoreById(@PathVariable String identifier) {
    Optional<Store> storeOptional = resolveStore(identifier);
    if (storeOptional.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    Store store = storeOptional.get();
    StoreResponse response = StoreResponse.fromEntity(store);
    return ResponseEntity.ok(response);
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

  private Optional<Store> resolveStore(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      return Optional.empty();
    }
    String trimmed = identifier.trim();
    try {
      UUID uid = UUID.fromString(trimmed);
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

  @PatchMapping("/{identifier}")
  @RequiresRole({"ADMIN", "MERCHANT"})
  @Operation(
      summary =
          "Mise à jour partielle des informations du commerce (SLE-54, SLE-245) - Réservé aux administrateurs et commerçants propriétaires")
  public ResponseEntity<StoreResponse> patchStore(
      @PathVariable String identifier,
      @Valid @RequestBody StorePatchRequest request,
      HttpServletRequest servletRequest) {
    Long authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String userRole = extractAuthenticatedUserRole(servletRequest);
    Store updatedStore =
        storeService.patchStore(identifier, request, authenticatedUserId, userRole);
    return ResponseEntity.ok(StoreResponse.fromEntity(updatedStore));
  }

  @PatchMapping(value = "/{identifier}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresRole({"ADMIN", "MERCHANT"})
  @Operation(
      summary =
          "Mettre à jour la photo de vitrine du commerce sur S3 (SLE-54, SLE-246) - Réservé aux administrateurs et commerçants propriétaires")
  public ResponseEntity<StoreResponse> updateStoreImage(
      @PathVariable String identifier,
      @RequestPart("image") MultipartFile image,
      HttpServletRequest servletRequest) {
    Long authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String userRole = extractAuthenticatedUserRole(servletRequest);
    Store updatedStore =
        storeService.updateStoreImage(identifier, image, authenticatedUserId, userRole);
    return ResponseEntity.ok(StoreResponse.fromEntity(updatedStore));
  }

  @GetMapping("/{identifier}/absences")
  @Operation(summary = "Consulter la liste des absences d'un commerce (SLE-54)")
  public ResponseEntity<List<StoreAbsenceResponse>> getStoreAbsences(
      @PathVariable String identifier) {
    Store store =
        storeService
            .getStoreByIdOrUid(identifier)
            .orElseThrow(
                () -> new com.shoploc.store_service.exception.StoreNotFoundException(identifier));
    List<StoreAbsenceResponse> responses =
        store.getAbsences().stream()
            .map(StoreAbsenceResponse::fromEntity)
            .collect(Collectors.toList());
    return ResponseEntity.ok(responses);
  }

  @PostMapping("/{identifier}/absences")
  @RequiresRole({"ADMIN", "MERCHANT"})
  @Operation(summary = "Ajouter une absence à la liste d'un commerce (SLE-54)")
  public ResponseEntity<StoreResponse> addStoreAbsence(
      @PathVariable String identifier,
      @Valid @RequestBody StoreAbsenceRequest request,
      HttpServletRequest servletRequest) {
    Long authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String userRole = extractAuthenticatedUserRole(servletRequest);
    Store updatedStore =
        storeService.addStoreAbsence(identifier, request, authenticatedUserId, userRole);
    return ResponseEntity.status(HttpStatus.CREATED).body(StoreResponse.fromEntity(updatedStore));
  }

  @PutMapping("/{identifier}/absences/{absenceId}")
  @RequiresRole({"ADMIN", "MERCHANT"})
  @Operation(summary = "Modifier une absence spécifique d'un commerce (SLE-54)")
  public ResponseEntity<StoreResponse> updateStoreAbsence(
      @PathVariable String identifier,
      @PathVariable Long absenceId,
      @Valid @RequestBody StoreAbsenceRequest request,
      HttpServletRequest servletRequest) {
    Long authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String userRole = extractAuthenticatedUserRole(servletRequest);
    Store updatedStore =
        storeService.updateStoreAbsence(
            identifier, absenceId, request, authenticatedUserId, userRole);
    return ResponseEntity.ok(StoreResponse.fromEntity(updatedStore));
  }

  @DeleteMapping("/{identifier}/absences/{absenceId}")
  @RequiresRole({"ADMIN", "MERCHANT"})
  @Operation(summary = "Supprimer une absence spécifique d'un commerce (SLE-54)")
  public ResponseEntity<StoreResponse> deleteStoreAbsenceById(
      @PathVariable String identifier,
      @PathVariable Long absenceId,
      HttpServletRequest servletRequest) {
    Long authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String userRole = extractAuthenticatedUserRole(servletRequest);
    Store updatedStore =
        storeService.deleteStoreAbsenceById(identifier, absenceId, authenticatedUserId, userRole);
    return ResponseEntity.ok(StoreResponse.fromEntity(updatedStore));
  }

  @DeleteMapping("/{identifier}/absence")
  @RequiresRole({"ADMIN", "MERCHANT"})
  @Operation(
      summary =
          "Supprimer ou réinitialiser toutes les absences d'un commerce (SLE-54) - Réservé aux administrateurs et commerçants propriétaires")
  public ResponseEntity<StoreResponse> deleteStoreAbsence(
      @PathVariable String identifier, HttpServletRequest servletRequest) {
    Long authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String userRole = extractAuthenticatedUserRole(servletRequest);
    Store updatedStore = storeService.clearStoreAbsence(identifier, authenticatedUserId, userRole);
    return ResponseEntity.ok(StoreResponse.fromEntity(updatedStore));
  }

  @PostMapping("/{identifier}/revoke")
  @RequiresRole("ADMIN")
  @Operation(
      summary =
          "Révoquer un commerce partenaire sortant du programme (SLE-70, SLE-255) - Réservé aux administrateurs")
  public ResponseEntity<StoreResponse> revokeStore(
      @PathVariable String identifier, HttpServletRequest servletRequest) {
    String adminEmail =
        servletRequest != null
            ? (String) servletRequest.getAttribute("authenticatedUserEmail")
            : null;
    if (adminEmail == null || adminEmail.isBlank()) {
      adminEmail = "ADMIN";
    }
    Store revokedStore = storeService.revokeStore(identifier, adminEmail);
    return ResponseEntity.ok(StoreResponse.fromEntity(revokedStore));
  }

  private Long extractAuthenticatedUserId(HttpServletRequest servletRequest) {
    if (servletRequest == null) {
      return null;
    }
    Object dbIdObj = servletRequest.getAttribute("authenticatedUserDbId");
    if (dbIdObj instanceof Long l) {
      return l;
    } else if (dbIdObj instanceof Number n) {
      return n.longValue();
    }
    return null;
  }

  private String extractAuthenticatedUserRole(HttpServletRequest servletRequest) {
    return servletRequest != null
        ? (String) servletRequest.getAttribute("authenticatedUserRole")
        : null;
  }

  private void resolveAndValidateUserId(
      StoreCreateRequest request, HttpServletRequest servletRequest) {
    String role = extractAuthenticatedUserRole(servletRequest);
    Long authenticatedDbId = extractAuthenticatedUserId(servletRequest);

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
