package com.shoploc.store_service.controller;

import com.shoploc.store_service.dto.StoreAbsenceRequest;
import com.shoploc.store_service.dto.StoreAbsenceResponse;
import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.dto.StorePatchRequest;
import com.shoploc.store_service.dto.StoreReactivateRequest;
import com.shoploc.store_service.dto.StoreResponse;
import com.shoploc.store_service.entity.Store;
import com.shoploc.store_service.exception.AccessDeniedException;
import com.shoploc.store_service.exception.StoreNotFoundException;
import com.shoploc.store_service.security.RequiresRole;
import com.shoploc.store_service.security.RoleInterceptor;
import com.shoploc.store_service.service.StoreService;
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
@RequestMapping({"/v1/stores", "/api/v1/stores"})
@Tag(name = "Stores", description = "Gestion des commerces")
public class StoreController {

  private final StoreService storeService;

  public StoreController(StoreService storeService) {
    this.storeService = storeService;
  }

  @GetMapping("/page")
  @Operation(
      summary = "Récupère les commerces paginés (accès public - filtré sur actifs par défaut)")
  public ResponseEntity<com.shoploc.store_service.dto.PageResponse<StoreResponse>>
      getStoresPaginated(
          @RequestParam(required = false) UUID userId,
          @RequestParam(required = false, defaultValue = "false") boolean includeRevoked,
          @RequestParam(defaultValue = "0") int page,
          @RequestParam(defaultValue = "20") int size) {
    List<StoreResponse> all = getAllStores(userId, includeRevoked);
    int total = all.size();
    int fromIndex = Math.min(page * size, total);
    int toIndex = Math.min(fromIndex + size, total);
    List<StoreResponse> content = all.subList(fromIndex, toIndex);
    int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
    return ResponseEntity.ok(
        com.shoploc.store_service.dto.PageResponse.of(content, page, size, total, totalPages));
  }

  @GetMapping
  @Operation(summary = "Récupère tous les commerces (accès public - filtré sur actifs par défaut)")
  public List<StoreResponse> getAllStores(
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false, defaultValue = "false") boolean includeRevoked) {
    List<Store> stores;
    if (userId != null) {
      stores = storeService.getStoresByOwnerUid(userId);
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

  public List<StoreResponse> getAllStores(UUID userId) {
    return getAllStores(userId, false);
  }

  @GetMapping("/me")
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary = "Récupère les commerces du commerçant authentifié via son token JWT (anti-IDOR)")
  public List<StoreResponse> getMyStores(HttpServletRequest servletRequest) {
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    if (authenticatedUserId == null) {
      throw new AccessDeniedException(
          "Identifiant commerçant introuvable dans le jeton d'authentification");
    }
    List<Store> stores = storeService.getStoresByOwnerUid(authenticatedUserId);
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

  @GetMapping("/{storeUid}")
  @Operation(summary = "Récupère un commerce par son uid UUID (accès public)")
  public ResponseEntity<StoreResponse> getStoreByUid(@PathVariable UUID storeUid) {
    Optional<Store> storeOptional = storeService.getStoreByUid(storeUid);
    if (storeOptional.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    Store store = storeOptional.get();
    StoreResponse response = StoreResponse.fromEntity(store);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{storeUid}/image")
  @Operation(
      summary =
          "Récupérer l'image binaire de la devanture d'un commerce hébergée sur S3 (avec Cache-Control - Accès public)")
  public ResponseEntity<byte[]> getStoreImage(@PathVariable UUID storeUid) {
    ImageResource resource = storeService.getStoreImage(storeUid);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(resource.getContentType()))
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
        .body(resource.getBytes());
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
    resolveAndValidateOwnerUid(request, servletRequest);
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
    resolveAndValidateOwnerUid(request, servletRequest);
    Store createdStore = storeService.createStore(request);
    StoreResponse response = StoreResponse.fromEntity(createdStore);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PatchMapping("/{storeUid}")
  @RequiresRole({"ADMIN", "MERCHANT"})
  @Operation(
      summary =
          "Mise à jour partielle des informations du commerce (SLE-54, SLE-245) - Réservé aux administrateurs et commerçants propriétaires")
  public ResponseEntity<StoreResponse> patchStore(
      @PathVariable UUID storeUid,
      @Valid @RequestBody StorePatchRequest request,
      HttpServletRequest servletRequest) {
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String userRole = extractAuthenticatedUserRole(servletRequest);
    Store updatedStore = storeService.patchStore(storeUid, request, authenticatedUserId, userRole);
    return ResponseEntity.ok(StoreResponse.fromEntity(updatedStore));
  }

  @PatchMapping(value = "/{storeUid}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresRole({"ADMIN", "MERCHANT"})
  @Operation(
      summary =
          "Mettre à jour la photo de vitrine du commerce sur S3 (SLE-54, SLE-246) - Réservé aux administrateurs et commerçants propriétaires")
  public ResponseEntity<StoreResponse> updateStoreImage(
      @PathVariable UUID storeUid,
      @RequestPart("image") MultipartFile image,
      HttpServletRequest servletRequest) {
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String userRole = extractAuthenticatedUserRole(servletRequest);
    Store updatedStore =
        storeService.updateStoreImage(storeUid, image, authenticatedUserId, userRole);
    return ResponseEntity.ok(StoreResponse.fromEntity(updatedStore));
  }

  @GetMapping("/{storeUid}/absences")
  @Operation(summary = "Consulter la liste des absences d'un commerce (SLE-54)")
  public ResponseEntity<List<StoreAbsenceResponse>> getStoreAbsences(@PathVariable UUID storeUid) {
    Store store =
        storeService
            .getStoreByUid(storeUid)
            .orElseThrow(() -> new StoreNotFoundException(storeUid));
    List<StoreAbsenceResponse> responses =
        store.getAbsences().stream()
            .map(StoreAbsenceResponse::fromEntity)
            .collect(Collectors.toList());
    return ResponseEntity.ok(responses);
  }

  @PostMapping("/{storeUid}/absences")
  @RequiresRole({"ADMIN", "MERCHANT"})
  @Operation(summary = "Ajouter une absence à la liste d'un commerce (SLE-54)")
  public ResponseEntity<StoreResponse> addStoreAbsence(
      @PathVariable UUID storeUid,
      @Valid @RequestBody StoreAbsenceRequest request,
      HttpServletRequest servletRequest) {
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String userRole = extractAuthenticatedUserRole(servletRequest);
    Store updatedStore =
        storeService.addStoreAbsence(storeUid, request, authenticatedUserId, userRole);
    return ResponseEntity.status(HttpStatus.CREATED).body(StoreResponse.fromEntity(updatedStore));
  }

  @PutMapping("/{storeUid}/absences/{absenceUid}")
  @RequiresRole({"ADMIN", "MERCHANT"})
  @Operation(summary = "Modifier une absence spécifique d'un commerce (SLE-54)")
  public ResponseEntity<StoreResponse> updateStoreAbsence(
      @PathVariable UUID storeUid,
      @PathVariable UUID absenceUid,
      @Valid @RequestBody StoreAbsenceRequest request,
      HttpServletRequest servletRequest) {
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String userRole = extractAuthenticatedUserRole(servletRequest);
    Store updatedStore =
        storeService.updateStoreAbsence(
            storeUid, absenceUid, request, authenticatedUserId, userRole);
    return ResponseEntity.ok(StoreResponse.fromEntity(updatedStore));
  }

  @DeleteMapping("/{storeUid}/absences/{absenceUid}")
  @RequiresRole({"ADMIN", "MERCHANT"})
  @Operation(summary = "Supprimer une absence spécifique d'un commerce (SLE-54)")
  public ResponseEntity<StoreResponse> deleteStoreAbsenceByUid(
      @PathVariable UUID storeUid,
      @PathVariable UUID absenceUid,
      HttpServletRequest servletRequest) {
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String userRole = extractAuthenticatedUserRole(servletRequest);
    Store updatedStore =
        storeService.deleteStoreAbsenceByUid(storeUid, absenceUid, authenticatedUserId, userRole);
    return ResponseEntity.ok(StoreResponse.fromEntity(updatedStore));
  }

  @DeleteMapping("/{storeUid}/absence")
  @RequiresRole({"ADMIN", "MERCHANT"})
  @Operation(
      summary =
          "Supprimer ou réinitialiser toutes les absences d'un commerce (SLE-54) - Réservé aux administrateurs et commerçants propriétaires")
  public ResponseEntity<StoreResponse> deleteStoreAbsence(
      @PathVariable UUID storeUid, HttpServletRequest servletRequest) {
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String userRole = extractAuthenticatedUserRole(servletRequest);
    Store updatedStore = storeService.clearStoreAbsence(storeUid, authenticatedUserId, userRole);
    return ResponseEntity.ok(StoreResponse.fromEntity(updatedStore));
  }

  @PostMapping("/{storeUid}/revoke")
  @RequiresRole("ADMIN")
  @Operation(
      summary =
          "Révoquer un commerce partenaire sortant du programme (SLE-70, SLE-255) - Réservé aux administrateurs")
  public ResponseEntity<StoreResponse> revokeStore(
      @PathVariable UUID storeUid, HttpServletRequest servletRequest) {
    String adminEmail =
        servletRequest != null
            ? (String) servletRequest.getAttribute(RoleInterceptor.ATTR_USER_EMAIL)
            : null;
    if (adminEmail == null || adminEmail.isBlank()) {
      adminEmail = "ADMIN";
    }
    Store revokedStore = storeService.revokeStore(storeUid, adminEmail);
    return ResponseEntity.ok(StoreResponse.fromEntity(revokedStore, true));
  }

  @PostMapping("/{storeUid}/reactivate")
  @RequiresRole("ADMIN")
  @Operation(
      summary =
          "Réactiver un commerce partenaire révoqué avec nouveau RIB conventionnel (SLE-70, SLE-263) - Réservé aux administrateurs")
  public ResponseEntity<StoreResponse> reactivateStore(
      @PathVariable UUID storeUid, @Valid @RequestBody StoreReactivateRequest request) {
    Store reactivatedStore = storeService.reactivateStore(storeUid, request);
    return ResponseEntity.ok(StoreResponse.fromEntity(reactivatedStore));
  }

  private UUID extractAuthenticatedUserId(HttpServletRequest servletRequest) {
    if (servletRequest == null) {
      return null;
    }
    Object userIdObj = servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ID);
    if (userIdObj instanceof UUID uuid) {
      return uuid;
    }
    if (userIdObj instanceof String str && !str.isBlank()) {
      try {
        return UUID.fromString(str.trim());
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
    return null;
  }

  private String extractAuthenticatedUserRole(HttpServletRequest servletRequest) {
    return servletRequest != null
        ? (String) servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ROLE)
        : null;
  }

  private void resolveAndValidateOwnerUid(
      StoreCreateRequest request, HttpServletRequest servletRequest) {
    String role = extractAuthenticatedUserRole(servletRequest);
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);

    if ("MERCHANT".equalsIgnoreCase(role)) {
      // A merchant can only create a store for their own account (IDOR prevention)
      if (authenticatedUserId == null) {
        throw new AccessDeniedException(
            "Identifiant commerçant introuvable dans le jeton d'authentification");
      }
      if (request.getOwnerUid() != null && !request.getOwnerUid().equals(authenticatedUserId)) {
        throw new AccessDeniedException(
            "Accès refusé : un commerçant ne peut créer un commerce que pour son propre compte (id "
                + authenticatedUserId
                + ")");
      }
      request.setOwnerUid(authenticatedUserId);
    } else {
      // Administrator or test/internal context without servletRequest session
      if (request.getOwnerUid() == null) {
        request.setOwnerUid(authenticatedUserId);
      }
    }

    if (request.getOwnerUid() == null) {
      throw new IllegalArgumentException(
          "L'identifiant du partenaire propriétaire (ownerUid) est obligatoire et doit être un UUID valide.");
    }
  }
}
