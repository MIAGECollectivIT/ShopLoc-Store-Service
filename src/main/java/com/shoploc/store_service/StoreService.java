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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  public Optional<Store> getStoreByUid(UUID uid) {
    if (uid == null) {
      return Optional.empty();
    }
    return storeRepository.findByUid(uid);
  }

  @Transactional(readOnly = true)
  public List<Store> getStoresByOwnerUid(UUID ownerUid) {
    if (ownerUid == null) {
      return List.of();
    }
    return storeRepository.findByOwnerUid(ownerUid);
  }

  @Transactional(readOnly = true)
  public ImageResource getStoreImage(UUID storeUid) {
    Store store = findStoreByUidOrThrow(storeUid);

    if (store.getUrl() == null || store.getUrl().isBlank()) {
      throw new StoreImageNotFoundException(storeUid);
    }

    return imageStorageService
        .downloadImage(store.getUrl())
        .orElseThrow(() -> new StoreImageNotFoundException(storeUid));
  }

  @Transactional
  public Store revokeStore(UUID storeUid, String adminIdentifier) {
    Store store = findStoreByUidOrThrow(storeUid);

    if (store.isRevoked()) {
      throw new StoreAlreadyRevokedException(storeUid.toString());
    }

    if (store.getOwnerUid() == null) {
      throw new IllegalStateException(
          "Un commerce ne peut pas être révoqué s'il n'est pas lié à un commerçant.");
    }

    store.setStatus(StoreStatus.REVOKED);
    store.setRevokedAt(Instant.now());
    store.setRevokedBy(adminIdentifier);
    store.setRib("REVOKED");

    Store savedStore = storeRepository.save(store);

    if (savedStore.getOwnerUid() != null) {
      userClient.unlinkStoreFromUser(savedStore.getOwnerUid(), savedStore.getUid());
    }

    return savedStore;
  }

  @Transactional
  public Store reactivateStore(UUID storeUid, StoreReactivateRequest request) {
    Store store = findStoreByUidOrThrow(storeUid);

    if (!store.isRevoked()) {
      throw new StoreNotRevokedException(storeUid.toString());
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

    // Gestion de la réaffectation du commerçant lors de la réactivation
    UUID targetOwnerUid = request.ownerUid() != null ? request.ownerUid() : store.getOwnerUid();
    if (targetOwnerUid == null) {
      throw new IllegalArgumentException(
          "Un commerce ne peut pas être orphelin : l'identifiant du commerçant réaffecté doit être valide");
    }

    if (!userClient.userExists(targetOwnerUid)) {
      throw new UserNotFoundException(targetOwnerUid);
    }

    UUID oldOwnerUid = store.getOwnerUid();
    store.setOwnerUid(targetOwnerUid);
    store.setStatus(StoreStatus.ACTIVE);
    store.setRib(cleanedRib);
    store.setRevokedAt(null);
    store.setRevokedBy(null);

    Store savedStore = storeRepository.save(store);

    // Relier systématiquement le commerce réactivé à son commerçant dans users-service
    userClient.linkStoreToUser(targetOwnerUid, savedStore.getUid());
    if (oldOwnerUid != null && !oldOwnerUid.equals(targetOwnerUid)) {
      userClient.unlinkStoreFromUser(oldOwnerUid, savedStore.getUid());
    }

    return savedStore;
  }

  public Store createStore(StoreCreateRequest request, MultipartFile image) {
    UUID ownerUid = request.getOwnerUid();
    if (!userClient.userExists(ownerUid)) {
      throw new UserNotFoundException(ownerUid);
    }

    Store storeToSave = request.toEntity();

    if (image != null && !image.isEmpty()) {
      String imageUrl = imageStorageService.uploadStoreImage(image);
      storeToSave.setUrl(imageUrl);
    }

    Store savedStore = storeRepository.save(storeToSave);

    userClient.linkStoreToUser(ownerUid, savedStore.getUid());

    return savedStore;
  }

  public Store createStore(StoreCreateRequest request) {
    return createStore(request, null);
  }

  @Transactional
  public Store patchStore(
      UUID storeUid, StorePatchRequest request, UUID authenticatedUserId, String userRole) {
    Store store = findStoreByUidOrThrow(storeUid);

    boolean isAdmin = "ADMIN".equalsIgnoreCase(userRole);

    if (store.isRevoked()) {
      // Seul un administrateur peut modifier un commerce révoqué, et uniquement pour lui réaffecter
      // un nouveau propriétaire
      if (!isAdmin || request.getOwnerUid() == null) {
        throw new RevokedStoreException(storeUid.toString());
      }
      UUID newOwnerUid = request.getOwnerUid();
      if (!userClient.userExists(newOwnerUid)) {
        throw new UserNotFoundException(newOwnerUid);
      }
      UUID oldOwnerUid = store.getOwnerUid();
      store.setOwnerUid(newOwnerUid);
      Store savedStore = storeRepository.save(store);

      userClient.linkStoreToUser(newOwnerUid, savedStore.getUid());
      if (oldOwnerUid != null && !oldOwnerUid.equals(newOwnerUid)) {
        userClient.unlinkStoreFromUser(oldOwnerUid, savedStore.getUid());
      }
      return savedStore;
    }

    verifyStoreOwnership(store, authenticatedUserId, userRole);

    if (request.getOwnerUid() != null) {
      if (!isAdmin) {
        throw new AccessDeniedException(
            "Seul un administrateur est autorisé à réassigner le propriétaire d'un commerce");
      }
      UUID newOwnerUid = request.getOwnerUid();
      if (!userClient.userExists(newOwnerUid)) {
        throw new UserNotFoundException(newOwnerUid);
      }
      UUID oldOwnerUid = store.getOwnerUid();
      store.setOwnerUid(newOwnerUid);
      userClient.linkStoreToUser(newOwnerUid, store.getUid());
      if (oldOwnerUid != null && !oldOwnerUid.equals(newOwnerUid)) {
        userClient.unlinkStoreFromUser(oldOwnerUid, store.getUid());
      }
    }

    if (request.getName() != null && !request.getName().isBlank()) {
      store.setName(request.getName().trim());
    }
    if (request.getAddress() != null && !request.getAddress().isBlank()) {
      store.setAddress(request.getAddress().trim());
    }
    if (request.getLatitude() != null) {
      store.setLatitude(request.getLatitude());
    }
    if (request.getLongitude() != null) {
      store.setLongitude(request.getLongitude());
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
    if (request.getOwnerUid() != null && "ADMIN".equalsIgnoreCase(userRole)) {
      store.setOwnerUid(request.getOwnerUid());
    }
    if (Boolean.TRUE.equals(request.getClearAbsence())) {
      store.clearAbsences();
    } else if (request.getAbsences() != null) {
      applyBulkAbsencesUpdate(store, request.getAbsences());
    } else {
      applyLegacySingleAbsenceUpdate(store, request);
    }

    return storeRepository.save(store);
  }

  private void applyBulkAbsencesUpdate(Store store, List<StoreAbsenceRequest> absenceRequests) {
    Map<UUID, StoreAbsence> existingByUid = new HashMap<>();
    for (StoreAbsence existing : store.getAbsences()) {
      if (existing.getUid() != null) {
        existingByUid.put(existing.getUid(), existing);
      }
    }
    Set<UUID> retainedUids = new HashSet<>();
    List<StoreAbsence> toAdd = new ArrayList<>();

    for (StoreAbsenceRequest absenceReq : absenceRequests) {
      if (absenceReq.getStartDate() == null) {
        throw new IllegalArgumentException("La date de début est obligatoire pour chaque absence");
      }
      if (absenceReq.getEndDate() != null
          && absenceReq.getEndDate().isBefore(absenceReq.getStartDate())) {
        throw new IllegalArgumentException(
            "La date de fin d'absence ne peut pas être antérieure à la date de début");
      }
      String trimmedReason = absenceReq.getReason() != null ? absenceReq.getReason().trim() : null;
      UUID reqUid = absenceReq.getUid();
      if (reqUid != null && existingByUid.containsKey(reqUid) && retainedUids.add(reqUid)) {
        StoreAbsence existing = existingByUid.get(reqUid);
        existing.setStartDate(absenceReq.getStartDate());
        existing.setEndDate(absenceReq.getEndDate());
        existing.setReason(trimmedReason);
      } else {
        StoreAbsence created =
            new StoreAbsence(
                store, absenceReq.getStartDate(), absenceReq.getEndDate(), trimmedReason);
        if (reqUid != null && !existingByUid.containsKey(reqUid) && retainedUids.add(reqUid)) {
          created.setUid(reqUid);
        }
        toAdd.add(created);
      }
    }

    store.getAbsences().removeIf(a -> a.getUid() == null || !retainedUids.contains(a.getUid()));
    store.getAbsences().addAll(toAdd);
    store.syncCurrentAbsenceFields();
  }

  private void applyLegacySingleAbsenceUpdate(Store store, StorePatchRequest request) {
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

  @Transactional
  public Store addStoreAbsence(
      UUID storeUid, StoreAbsenceRequest request, UUID authenticatedUserId, String userRole) {
    Store store = findStoreByUidOrThrow(storeUid);
    if (store.isRevoked()) {
      throw new RevokedStoreException(storeUid.toString());
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
    if (request.getUid() != null
        && store.getAbsences().stream().noneMatch(a -> request.getUid().equals(a.getUid()))) {
      absence.setUid(request.getUid());
    }
    store.addAbsence(absence);
    return storeRepository.save(store);
  }

  @Transactional
  public Store updateStoreAbsence(
      UUID storeUid,
      UUID absenceUid,
      StoreAbsenceRequest request,
      UUID authenticatedUserId,
      String userRole) {
    Store store = findStoreByUidOrThrow(storeUid);
    if (store.isRevoked()) {
      throw new RevokedStoreException(storeUid.toString());
    }
    verifyStoreOwnership(store, authenticatedUserId, userRole);

    StoreAbsence absence =
        store.getAbsences().stream()
            .filter(a -> a.getUid() != null && a.getUid().equals(absenceUid))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException("Absence introuvable avec l'UID: " + absenceUid));

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
  public Store deleteStoreAbsenceByUid(
      UUID storeUid, UUID absenceUid, UUID authenticatedUserId, String userRole) {
    Store store = findStoreByUidOrThrow(storeUid);
    if (store.isRevoked()) {
      throw new RevokedStoreException(storeUid.toString());
    }
    verifyStoreOwnership(store, authenticatedUserId, userRole);

    StoreAbsence absence =
        store.getAbsences().stream()
            .filter(a -> a.getUid() != null && a.getUid().equals(absenceUid))
            .findFirst()
            .orElse(null);

    if (absence != null) {
      store.removeAbsence(absence);
    }
    return storeRepository.save(store);
  }

  @Transactional
  public Store clearStoreAbsence(UUID storeUid, UUID authenticatedUserId, String userRole) {
    Store store = findStoreByUidOrThrow(storeUid);
    if (store.isRevoked()) {
      throw new RevokedStoreException(storeUid.toString());
    }
    verifyStoreOwnership(store, authenticatedUserId, userRole);
    store.clearAbsences();
    return storeRepository.save(store);
  }

  public Store updateStoreImage(
      UUID storeUid, MultipartFile image, UUID authenticatedUserId, String userRole) {
    Store store = findStoreByUidOrThrow(storeUid);
    if (store.isRevoked()) {
      throw new RevokedStoreException(storeUid.toString());
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

  private Store findStoreByUidOrThrow(UUID storeUid) {
    if (storeUid == null) {
      throw new StoreNotFoundException((UUID) null);
    }
    return storeRepository
        .findByUid(storeUid)
        .orElseThrow(() -> new StoreNotFoundException(storeUid));
  }

  private void verifyStoreOwnership(Store store, UUID authenticatedUserId, String userRole) {
    if ("ADMIN".equalsIgnoreCase(userRole)) {
      return;
    }
    if (authenticatedUserId != null && authenticatedUserId.equals(store.getOwnerUid())) {
      return;
    }
    throw new AccessDeniedException(
        "Accès refusé : vous n'avez pas l'autorisation de modifier ce commerce");
  }
}
