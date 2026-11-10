package com.shoploc.store_service;

import com.shoploc.store_service.client.UserClient;
import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.exception.StoreImageNotFoundException;
import com.shoploc.store_service.exception.StoreNotFoundException;
import com.shoploc.store_service.exception.UserNotFoundException;
import com.shoploc.store_service.storage.ImageResource;
import com.shoploc.store_service.storage.ImageStorageService;
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

    if (store.getUrl() == null || store.getUrl().isBlank()) {
      throw new StoreImageNotFoundException(store.getId());
    }

    return imageStorageService
        .downloadImage(store.getUrl())
        .orElseThrow(() -> new StoreImageNotFoundException(store.getId()));
  }

  @Transactional(readOnly = true)
  public ImageResource getStoreImage(Long storeId) {
    return getStoreImage(String.valueOf(storeId));
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
}
