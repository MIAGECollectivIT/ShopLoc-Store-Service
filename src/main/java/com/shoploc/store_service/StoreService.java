package com.shoploc.store_service;

import com.shoploc.store_service.client.UserClient;
import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.dto.StorePatchRequest;
import com.shoploc.store_service.exception.AccessDeniedException;
import com.shoploc.store_service.exception.StoreImageNotFoundException;
import com.shoploc.store_service.exception.StoreNotFoundException;
import com.shoploc.store_service.exception.UserNotFoundException;
import com.shoploc.store_service.storage.ImageResource;
import com.shoploc.store_service.storage.ImageStorageService;
import com.shoploc.store_service.validation.RibIbanHelper;
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

  public List<Store> getAllStores() {
    return storeRepository.findAll();
  }

  public Optional<Store> getStoreById(Long id) {
    return storeRepository.findById(id);
  }

  public Optional<Store> getStoreByUid(UUID uid) {
    if (uid == null) {
      return Optional.empty();
    }
    return storeRepository.findByUid(uid);
  }

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

    if (store.getUrl() == null || store.getUrl().isBlank()) {
      throw new StoreImageNotFoundException(publicIdentifier);
    }

    return imageStorageService
        .downloadImage(store.getUrl())
        .orElseThrow(() -> new StoreImageNotFoundException(publicIdentifier));
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

    return storeRepository.save(store);
  }

  public Store updateStoreImage(
      String identifier, MultipartFile image, Long authenticatedUserId, String userRole) {
    Store store =
        getStoreByIdOrUid(identifier).orElseThrow(() -> new StoreNotFoundException(identifier));

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
    if (authenticatedUserId == null || !authenticatedUserId.equals(store.getUserId())) {
      throw new AccessDeniedException(
          "Accès refusé : vous n'avez pas l'autorisation de modifier ce commerce");
    }
  }
}
