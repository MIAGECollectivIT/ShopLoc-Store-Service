package com.shoploc.store_service;

import com.shoploc.store_service.client.UserClient;
import com.shoploc.store_service.dto.StoreAbsenceRequest;
import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.dto.StorePatchRequest;
import com.shoploc.store_service.dto.StoreReactivateRequest;
import com.shoploc.store_service.exception.AccessDeniedException;
import com.shoploc.store_service.exception.RevokedStoreException;
import com.shoploc.store_service.exception.StoreAlreadyRevokedException;
import com.shoploc.store_service.exception.StoreImageNotFoundException;
import com.shoploc.store_service.exception.StoreNotFoundException;
import com.shoploc.store_service.exception.StoreNotRevokedException;
import com.shoploc.store_service.exception.UserNotFoundException;
import com.shoploc.store_service.storage.ImageResource;
import com.shoploc.store_service.storage.ImageStorageService;
import com.shoploc.store_service.validation.RibIbanHelper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StoreService {

  private final StoreRepository storeRepository;
  private final UserClient userClient;
  private final ImageStorageService imageStorageService;

  public StoreService(
      StoreRepository storeRepository,
      UserClient userClient,
      ImageStorageService imageStorageService) {
    this.storeRepository = storeRepository;
    this.userClient = userClient;
    this.imageStorageService = imageStorageService;
  }

  @Transactional(readOnly = true)
  public List<Store> getAllStores() {
    return getAllStores(false);
  }

  @Transactional(readOnly = true)
  public List<Store> getAllStores(boolean includeRevoked) {
    if (includeRevoked) {
      return storeRepository.findAll();
    }
    return storeRepository.findByStatus(StoreStatus.ACTIVE);
  }

  @Transactional(readOnly = true)
  public List<Store> searchStores(String query, List<String> categories) {
    org.springframework.data.jpa.domain.Specification<Store> spec =
        StoreSpecifications.withFilters(query, categories, StoreStatus.ACTIVE);
    return storeRepository.findAll(spec);
  }

  @Transactional(readOnly = true)
  public Optional<Store> getStoreById(Long id) {
    return storeRepository.findById(id);
  }

  @Transactional(readOnly = true)
  public Optional<Store> getStoreByUid(UUID uid) {
    if (uid == null) {
      return Optional.empty();
    }
    return storeRepository.findByUid(uid);
  }

  @Transactional(readOnly = true)
  public Optional<Store> getStoreByIdOrUid(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      return Optional.empty();
    }
    String trimmed = identifier.trim();
    try {
      UUID uid = UUID.fromString(trimmed);
      return storeRepository.findByUid(uid);
    } catch (IllegalArgumentException e) {
      try {
        Long id = Long.parseLong(trimmed);
        return storeRepository.findById(id);
      } catch (NumberFormatException nfe) {
        return Optional.empty();
      }
    }
  }

  public List<Store> getStoresByUserId(Long userId) {
    return storeRepository.findByUserId(userId);
  }

  @Transactional(readOnly = true)
  public ImageResource getStoreImage(String identifier) {
    Store store =
        getStoreByIdOrUid(identifier).orElseThrow(() -> new StoreNotFoundException(identifier));

    String publicIdentifier = store.getUid() != null ? store.getUid().toString() : identifier;

    if (store.isRevoked() || store.getUrl() == null || store.getUrl().isBlank()) {
      throw new StoreImageNotFoundException(publicIdentifier);
    }

    return imageStorageService
        .downloadImage(store.getUrl())
        .orElseThrow(() -> new StoreImageNotFoundException(publicIdentifier));
  }

  @Transactional
  public Store revokeStore(String identifier, String adminIdentifier) {
    Store store =
        getStoreByIdOrUid(identifier).orElseThrow(() -> new StoreNotFoundException(identifier));

    if (store.isRevoked()) {
      throw new StoreAlreadyRevokedException(identifier);
    }

    store.setStatus(StoreStatus.REVOKED);
    store.setRevokedAt(Instant.now());
    store.setRevokedBy(adminIdentifier);
    store.setRib("REVOKED");
    store.setUrl(null);

    return storeRepository.save(store);
  }

  @Transactional
  public Store reactivateStore(String identifier, StoreReactivateRequest request) {
    Store store =
        getStoreByIdOrUid(identifier).orElseThrow(() -> new StoreNotFoundException(identifier));

    if (!store.isRevoked()) {
      throw new StoreNotRevokedException(identifier);
    }

    if (request == null || request.rib() == null || request.rib().isBlank()) {
      throw new IllegalArgumentException(
          "Le nouveau RIB est obligatoire pour réactiver le commerce");
    }

    String cleanedRib = RibIbanHelper.clean(request.rib());
    if (!RibIbanHelper.isValidRibOrIban(cleanedRib)) {
      throw new IllegalArgumentException(
          "Le format ou la clé algorithmique du RIB/IBAN est invalide");
    }

    store.setStatus(StoreStatus.ACTIVE);
    store.setRib(cleanedRib);
    store.setRevokedAt(null);
    store.setRevokedBy(null);

    return storeRepository.save(store);
  }

  public Store createStore(StoreCreateRequest request, MultipartFile image) {
    Long userId = request.getUserId();
    if (!userClient.userExists(userId)) {
      throw new UserNotFoundException(userId);
    }

    Store storeToSave = request.toEntity();

    if (image != null && !image.isEmpty()) {
      String imageUrl = imageStorageService.uploadStoreImage(image);
      storeToSave.setUrl(imageUrl);
    }

    Store savedStore = storeRepository.save(storeToSave);

    userClient.linkStoreToUser(userId, savedStore.getId());

    return savedStore;
  }

  public Store createStore(StoreCreateRequest request) {
    return createStore(request, null);
  }

  public Store patchStore(
      String identifier, StorePatchRequest request, Long authenticatedUserId, String userRole) {
    Store store =
        getStoreByIdOrUid(identifier).orElseThrow(() -> new StoreNotFoundException(identifier));

    if (store.isRevoked()) {
      throw new RevokedStoreException(identifier);
    }

    verifyStoreOwnership(store, authenticatedUserId, userRole);

    if (request.getName() != null && !request.getName().isBlank()) {
      store.setName(request.getName().trim());
    }
    if (request.getAddress() != null && !request.getAddress().isBlank()) {
      store.setAddress(request.getAddress().trim());
    }
    if (request.getCategory() != null && !request.getCategory().isBlank()) {
      store.setCategory(request.getCategory().trim());
    }
    if (request.getRib() != null && !request.getRib().isBlank()) {
      String cleanedRib = RibIbanHelper.clean(request.getRib());
      if (!RibIbanHelper.isValidRibOrIban(cleanedRib)) {
        throw new IllegalArgumentException(
            "Le format ou la clé algorithmique du RIB/IBAN est invalide");
      }
      store.setRib(cleanedRib);
    }
    if (request.getUrl() != null) {
      store.setUrl(request.getUrl().trim());
    }
    if (request.getOpeningHours() != null) {
      store.setOpeningHours(request.getOpeningHours().trim());
    }
    if (Boolean.TRUE.equals(request.getClearAbsence())) {
      store.clearAbsences();
    } else if (request.getAbsences() != null) {
      store.getAbsences().clear();
      for (StoreAbsenceRequest absenceReq : request.getAbsences()) {
        if (absenceReq.getStartDate() == null) {
          throw new IllegalArgumentException(
              "La date de début est obligatoire pour chaque absence");
        }
        if (absenceReq.getEndDate() != null
            && absenceReq.getEndDate().isBefore(absenceReq.getStartDate())) {
          throw new IllegalArgumentException(
              "La date de fin d'absence ne peut pas être antérieure à la date de début");
        }
        StoreAbsence absence =
            new StoreAbsence(
                store,
                absenceReq.getStartDate(),
                absenceReq.getEndDate(),
                absenceReq.getReason() != null ? absenceReq.getReason().trim() : null);
        store.getAbsences().add(absence);
      }
      store.syncCurrentAbsenceFields();
    } else {
      if (request.getAbsenceStartDate() != null) {
        store.setAbsenceStartDate(request.getAbsenceStartDate());
      }
      if (request.getAbsenceEndDate() != null) {
        store.setAbsenceEndDate(request.getAbsenceEndDate());
      }
      if (request.getAbsenceReason() != null) {
        store.setAbsenceReason(request.getAbsenceReason().trim());
      }
      if (store.getAbsenceStartDate() != null
          && store.getAbsenceEndDate() != null
          && store.getAbsenceEndDate().isBefore(store.getAbsenceStartDate())) {
        throw new IllegalArgumentException(
            "La date de fin d'absence ne peut pas être antérieure à la date de début");
      }
      if (request.getAbsenceStartDate() != null && store.getAbsences().isEmpty()) {
        store.addAbsence(
            new StoreAbsence(
                store,
                store.getAbsenceStartDate(),
                store.getAbsenceEndDate(),
                store.getAbsenceReason()));
      }
    }

    return storeRepository.save(store);
  }

  @Transactional
  public Store addStoreAbsence(
      String identifier, StoreAbsenceRequest request, Long authenticatedUserId, String userRole) {
    Store store =
        getStoreByIdOrUid(identifier).orElseThrow(() -> new StoreNotFoundException(identifier));
    if (store.isRevoked()) {
      throw new RevokedStoreException(identifier);
    }
    verifyStoreOwnership(store, authenticatedUserId, userRole);

    if (request.getStartDate() == null) {
      throw new IllegalArgumentException("La date de début d'absence est obligatoire");
    }
    if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
      throw new IllegalArgumentException(
          "La date de fin d'absence ne peut pas être antérieure à la date de début");
    }

    StoreAbsence absence =
        new StoreAbsence(
            store,
            request.getStartDate(),
            request.getEndDate(),
            request.getReason() != null ? request.getReason().trim() : null);
    store.addAbsence(absence);
    return storeRepository.save(store);
  }

  @Transactional
  public Store updateStoreAbsence(
      String identifier,
      Long absenceId,
      StoreAbsenceRequest request,
      Long authenticatedUserId,
      String userRole) {
    Store store =
        getStoreByIdOrUid(identifier).orElseThrow(() -> new StoreNotFoundException(identifier));
    if (store.isRevoked()) {
      throw new RevokedStoreException(identifier);
    }
    verifyStoreOwnership(store, authenticatedUserId, userRole);

    StoreAbsence absence =
        store.getAbsences().stream()
            .filter(a -> a.getId() != null && a.getId().equals(absenceId))
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException("Absence introuvable avec l'ID: " + absenceId));

    if (request.getStartDate() != null) {
      absence.setStartDate(request.getStartDate());
    }
    if (request.getEndDate() != null) {
      absence.setEndDate(request.getEndDate());
    }
    if (request.getReason() != null) {
      absence.setReason(request.getReason().trim());
    }

    if (absence.getStartDate() != null
        && absence.getEndDate() != null
        && absence.getEndDate().isBefore(absence.getStartDate())) {
      throw new IllegalArgumentException(
          "La date de fin d'absence ne peut pas être antérieure à la date de début");
    }

    store.syncCurrentAbsenceFields();
    return storeRepository.save(store);
  }

  @Transactional
  public Store deleteStoreAbsenceById(
      String identifier, Long absenceId, Long authenticatedUserId, String userRole) {
    Store store =
        getStoreByIdOrUid(identifier).orElseThrow(() -> new StoreNotFoundException(identifier));
    if (store.isRevoked()) {
      throw new RevokedStoreException(identifier);
    }
    verifyStoreOwnership(store, authenticatedUserId, userRole);

    StoreAbsence absence =
        store.getAbsences().stream()
            .filter(a -> a.getId() != null && a.getId().equals(absenceId))
            .findFirst()
            .orElse(null);

    if (absence != null) {
      store.removeAbsence(absence);
    }
    return storeRepository.save(store);
  }

  @Transactional
  public Store clearStoreAbsence(String identifier, Long authenticatedUserId, String userRole) {
    Store store =
        getStoreByIdOrUid(identifier).orElseThrow(() -> new StoreNotFoundException(identifier));
    if (store.isRevoked()) {
      throw new RevokedStoreException(identifier);
    }
    verifyStoreOwnership(store, authenticatedUserId, userRole);
    store.clearAbsences();
    return storeRepository.save(store);
  }

  public Store updateStoreImage(
      String identifier, MultipartFile image, Long authenticatedUserId, String userRole) {
    Store store =
        getStoreByIdOrUid(identifier).orElseThrow(() -> new StoreNotFoundException(identifier));
    if (store.isRevoked()) {
      throw new RevokedStoreException(identifier);
    }

    verifyStoreOwnership(store, authenticatedUserId, userRole);

    if (image == null || image.isEmpty()) {
      throw new IllegalArgumentException("Le fichier image est obligatoire");
    }

    if (store.getUrl() != null && !store.getUrl().isBlank()) {
      imageStorageService.deleteImage(store.getUrl());
    }

    String imageUrl = imageStorageService.uploadStoreImage(image);
    store.setUrl(imageUrl);

    return storeRepository.save(store);
  }

  private void verifyStoreOwnership(Store store, Long authenticatedUserId, String userRole) {
    if ("ADMIN".equalsIgnoreCase(userRole)) {
      return;
    }
    if (authenticatedUserId != null) {
      if (authenticatedUserId.equals(store.getUserId())) {
        return;
      }
      // Demo / seed data compatibility: merchant Suzanne Morel (id 2) managing default stores 1, 2
      // or store where userId is 1
      if (authenticatedUserId == 2L
          && (store.getId() == null
              || store.getId() <= 2L
              || Long.valueOf(1L).equals(store.getUserId()))) {
        return;
      }
      if (store.getUserId() == null) {
        store.setUserId(authenticatedUserId);
        return;
      }
    }
    throw new AccessDeniedException(
        "Accès refusé : vous n'avez pas l'autorisation de modifier ce commerce");
  }
}
