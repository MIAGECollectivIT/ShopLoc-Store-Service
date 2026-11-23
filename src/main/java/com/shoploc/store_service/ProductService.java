package com.shoploc.store_service;

import com.shoploc.store_service.client.UserClient;
import com.shoploc.store_service.dto.ProductCreateRequest;
import com.shoploc.store_service.dto.ProductResponse;
import com.shoploc.store_service.dto.ProductUpdateRequest;
import com.shoploc.store_service.dto.StockUpdateRequest;
import com.shoploc.store_service.exception.AccessDeniedException;
import com.shoploc.store_service.exception.InsufficientStockException;
import com.shoploc.store_service.exception.InvalidFileException;
import com.shoploc.store_service.exception.ProductImageNotFoundException;
import com.shoploc.store_service.exception.ProductNotFoundException;
import com.shoploc.store_service.exception.ProductReferenceAlreadyExistsException;
import com.shoploc.store_service.exception.RevokedStoreException;
import com.shoploc.store_service.exception.StoreNotFoundException;
import com.shoploc.store_service.service.StockAlertEmailService;
import com.shoploc.store_service.storage.ImageResource;
import com.shoploc.store_service.storage.ImageStorageService;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductService {

  private static final Logger log = LoggerFactory.getLogger(ProductService.class);

  private final ProductRepository productRepository;
  private final StoreRepository storeRepository;
  private final ImageStorageService imageStorageService;
  private final StockAlertEmailService stockAlertEmailService;
  private final UserClient userClient;

  public ProductService(
      ProductRepository productRepository,
      StoreRepository storeRepository,
      ImageStorageService imageStorageService) {
    this(productRepository, storeRepository, imageStorageService, null, null);
  }

  @Autowired
  public ProductService(
      ProductRepository productRepository,
      StoreRepository storeRepository,
      ImageStorageService imageStorageService,
      StockAlertEmailService stockAlertEmailService,
      UserClient userClient) {
    this.productRepository = productRepository;
    this.storeRepository = storeRepository;
    this.imageStorageService = imageStorageService;
    this.stockAlertEmailService = stockAlertEmailService;
    this.userClient = userClient;
  }

  @Transactional(readOnly = true)
  public List<ProductResponse> getProductsByStore(UUID storeUid) {
    Store store = findStoreByUidOrThrow(storeUid);
    if (store.isRevoked()) {
      return List.of();
    }
    return productRepository.findByStoreId(store.getId()).stream()
        .map(product -> ProductResponse.fromEntity(product, store.getUid()))
        .toList();
  }

  @Transactional(readOnly = true)
  public ProductResponse getProductByUid(UUID storeUid, UUID productUid) {
    Store store = findStoreByUidOrThrow(storeUid);
    if (store.isRevoked()) {
      throw new RevokedStoreException(storeUid.toString());
    }
    Product product =
        productRepository
            .findByStoreIdAndUid(store.getId(), productUid)
            .orElseThrow(() -> new ProductNotFoundException(productUid));
    return ProductResponse.fromEntity(product, store.getUid());
  }

  @Transactional
  public ProductResponse createProduct(
      UUID storeUid,
      ProductCreateRequest request,
      MultipartFile image,
      UUID authenticatedUserId,
      String userRole) {
    Store store = findStoreByUidOrThrow(storeUid);
    if (store.isRevoked()) {
      throw new RevokedStoreException(storeUid.toString());
    }
    validateStoreOwnership(store, authenticatedUserId, userRole);

    String normalizedRef = request.getReference().trim();
    if (productRepository.existsByStoreIdAndReference(store.getId(), normalizedRef)) {
      throw new ProductReferenceAlreadyExistsException(normalizedRef);
    }

    String imageUrl = request.getImageUrl();
    if (image != null && !image.isEmpty()) {
      imageUrl = imageStorageService.uploadProductImage(image);
      log.info(
          "Image uploadée avec succès pour le produit dans le magasin {} : {}", storeUid, imageUrl);
    }

    Product product =
        new Product(
            store.getId(),
            normalizedRef,
            request.getName().trim(),
            request.getDescription(),
            request.getPrice(),
            imageUrl,
            request.getStockQuantity() != null ? request.getStockQuantity() : 0,
            request.getAlertThreshold() != null ? request.getAlertThreshold() : 5);

    Product savedProduct = productRepository.save(product);
    log.info(
        "Nouveau produit '{}' (uid={}) créé avec succès dans le commerce {}",
        savedProduct.getName(),
        savedProduct.getUid(),
        storeUid);
    return ProductResponse.fromEntity(savedProduct, store.getUid());
  }

  @Transactional
  public ProductResponse updateProduct(
      UUID storeUid,
      UUID productUid,
      ProductUpdateRequest request,
      MultipartFile image,
      UUID authenticatedUserId,
      String userRole) {
    Store store = findStoreByUidOrThrow(storeUid);
    if (store.isRevoked()) {
      throw new RevokedStoreException(storeUid.toString());
    }
    validateStoreOwnership(store, authenticatedUserId, userRole);

    Product product =
        productRepository
            .findByStoreIdAndUid(store.getId(), productUid)
            .orElseThrow(() -> new ProductNotFoundException(productUid));

    String normalizedRef = request.getReference().trim();
    if (productRepository.existsByStoreIdAndReferenceAndUidNot(
        store.getId(), normalizedRef, productUid)) {
      throw new ProductReferenceAlreadyExistsException(normalizedRef);
    }

    product.setReference(normalizedRef);
    product.setName(request.getName().trim());
    product.setDescription(request.getDescription());
    product.setPrice(request.getPrice());

    if (request.getStockQuantity() != null) {
      product.setStockQuantity(request.getStockQuantity());
    }

    if (request.getAlertThreshold() != null) {
      product.setAlertThreshold(request.getAlertThreshold());
    }

    if (image != null && !image.isEmpty()) {
      String imageUrl = imageStorageService.uploadProductImage(image);
      product.setImageUrl(imageUrl);
    } else if (request.getImageUrl() != null) {
      product.setImageUrl(request.getImageUrl());
    }

    Product updatedProduct = productRepository.save(product);
    log.info(
        "Produit uid={} mis à jour avec succès dans le commerce {}",
        updatedProduct.getUid(),
        storeUid);
    return ProductResponse.fromEntity(updatedProduct, store.getUid());
  }

  @Transactional
  public void deleteProduct(
      UUID storeUid, UUID productUid, UUID authenticatedUserId, String userRole) {
    Store store = findStoreByUidOrThrow(storeUid);
    if (store.isRevoked()) {
      throw new RevokedStoreException(storeUid.toString());
    }
    validateStoreOwnership(store, authenticatedUserId, userRole);

    Product product =
        productRepository
            .findByStoreIdAndUid(store.getId(), productUid)
            .orElseThrow(() -> new ProductNotFoundException(productUid));

    if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
      imageStorageService.deleteImage(product.getImageUrl());
    }

    productRepository.delete(product);
    log.info("Produit uid={} supprimé avec succès du commerce {}", productUid, storeUid);
  }

  @Transactional
  public ProductResponse updateProductImage(
      UUID storeUid,
      UUID productUid,
      MultipartFile image,
      UUID authenticatedUserId,
      String userRole) {
    Store store = findStoreByUidOrThrow(storeUid);
    if (store.isRevoked()) {
      throw new RevokedStoreException(storeUid.toString());
    }
    validateStoreOwnership(store, authenticatedUserId, userRole);

    if (image == null || image.isEmpty()) {
      throw new InvalidFileException("Le fichier image ne peut pas être vide");
    }

    Product product =
        productRepository
            .findByStoreIdAndUid(store.getId(), productUid)
            .orElseThrow(() -> new ProductNotFoundException(productUid));

    if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
      imageStorageService.deleteImage(product.getImageUrl());
    }

    String newImageUrl = imageStorageService.uploadProductImage(image);
    product.setImageUrl(newImageUrl);

    Product saved = productRepository.save(product);
    log.info("Photo du produit uid={} mise à jour sur S3 : {}", productUid, newImageUrl);
    return ProductResponse.fromEntity(saved, store.getUid());
  }

  @Transactional
  public ProductResponse updateProductImageUrl(
      UUID storeUid, UUID productUid, String imageUrl, UUID authenticatedUserId, String userRole) {
    Store store = findStoreByUidOrThrow(storeUid);
    if (store.isRevoked()) {
      throw new RevokedStoreException(storeUid.toString());
    }
    validateStoreOwnership(store, authenticatedUserId, userRole);

    Product product =
        productRepository
            .findByStoreIdAndUid(store.getId(), productUid)
            .orElseThrow(() -> new ProductNotFoundException(productUid));

    product.setImageUrl(imageUrl);
    Product saved = productRepository.save(product);
    log.info("URL de photo du produit uid={} mise à jour : {}", productUid, imageUrl);
    return ProductResponse.fromEntity(saved, store.getUid());
  }

  @Transactional(readOnly = true)
  public ImageResource getProductImage(UUID storeUid, UUID productUid) {
    Store store = findStoreByUidOrThrow(storeUid);
    if (store.isRevoked()) {
      throw new RevokedStoreException(storeUid.toString());
    }
    Product product =
        productRepository
            .findByStoreIdAndUid(store.getId(), productUid)
            .orElseThrow(() -> new ProductNotFoundException(productUid));

    if (product.getImageUrl() == null || product.getImageUrl().isBlank()) {
      throw new ProductImageNotFoundException(productUid);
    }

    return imageStorageService
        .downloadImage(product.getImageUrl())
        .orElseThrow(() -> new ProductImageNotFoundException(productUid));
  }

  @Transactional
  public ProductResponse updateStock(
      UUID storeUid,
      UUID productUid,
      StockUpdateRequest request,
      UUID authenticatedUserId,
      String userRole) {
    Store store = findStoreByUidOrThrow(storeUid);
    if (store.isRevoked()) {
      throw new RevokedStoreException(storeUid.toString());
    }
    validateStockUpdateAuthorization(store, authenticatedUserId, userRole);

    Product product =
        productRepository
            .findByStoreIdAndUidForUpdate(store.getId(), productUid)
            .orElseThrow(() -> new ProductNotFoundException(productUid));

    int currentStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
    int targetStock;

    if (request.getNewQuantity() != null) {
      targetStock = request.getNewQuantity();
    } else if (request.getQuantityDelta() != null) {
      targetStock = currentStock + request.getQuantityDelta();
    } else {
      throw new IllegalArgumentException(
          "Au moins un des champs 'quantityDelta' ou 'newQuantity' doit être renseigné");
    }

    if (targetStock < 0) {
      throw new InsufficientStockException(
          "Stock insuffisant pour le produit "
              + productUid
              + " (actuel: "
              + currentStock
              + ", demandé: "
              + targetStock
              + ")");
    }

    product.setStockQuantity(targetStock);
    Product saved = productRepository.save(product);
    log.info(
        "Stock ajusté pour produit uid={} dans magasin {} : ancien={} -> nouveau={}",
        productUid,
        storeUid,
        currentStock,
        targetStock);

    int alertThreshold = saved.getAlertThreshold() != null ? saved.getAlertThreshold() : 5;
    if (targetStock <= alertThreshold && targetStock < currentStock) {
      triggerStockAlert(store, saved, targetStock, alertThreshold);
    }

    return ProductResponse.fromEntity(saved, store.getUid());
  }

  private void triggerStockAlert(
      Store store, Product product, int targetStock, int alertThreshold) {
    if (stockAlertEmailService == null) {
      return;
    }
    String merchantEmail = null;
    if (userClient != null && store.getOwnerUid() != null) {
      merchantEmail = userClient.getUserEmail(store.getOwnerUid()).orElse(null);
    }
    if (merchantEmail != null && !merchantEmail.isBlank()) {
      stockAlertEmailService.sendStockAlertAsync(
          merchantEmail,
          store.getName(),
          product.getName(),
          product.getReference(),
          targetStock,
          alertThreshold);
    } else {
      log.info(
          "Stock alert detected for store '{}', product '{}' (stock={}), but merchant email could not be resolved",
          store.getName(),
          product.getName(),
          targetStock);
    }
  }

  private Store findStoreByUidOrThrow(UUID storeUid) {
    if (storeUid == null) {
      throw new StoreNotFoundException((UUID) null);
    }
    return storeRepository
        .findByUid(storeUid)
        .orElseThrow(() -> new StoreNotFoundException(storeUid));
  }

  private void validateStoreOwnership(Store store, UUID authenticatedUserId, String userRole) {
    if ("ADMIN".equalsIgnoreCase(userRole)) {
      return;
    }

    if ("MERCHANT".equalsIgnoreCase(userRole)) {
      if (authenticatedUserId == null || !authenticatedUserId.equals(store.getOwnerUid())) {
        throw new AccessDeniedException(
            "Accès refusé : vous n'êtes pas le commerçant gestionnaire de ce commerce");
      }
      return;
    }

    if (authenticatedUserId == null || !authenticatedUserId.equals(store.getOwnerUid())) {
      throw new AccessDeniedException("Accès refusé : propriétaire du magasin non concordant");
    }
  }

  private void validateStockUpdateAuthorization(
      Store store, UUID authenticatedUserId, String userRole) {
    // SERVICE and ADMIN roles can modify stock for any store
    if ("SERVICE".equalsIgnoreCase(userRole) || "ADMIN".equalsIgnoreCase(userRole)) {
      return;
    }

    // The merchant can only modify stock for their own store
    if ("MERCHANT".equalsIgnoreCase(userRole)) {
      if (authenticatedUserId == null || !authenticatedUserId.equals(store.getOwnerUid())) {
        throw new AccessDeniedException(
            "Accès refusé : vous n'êtes pas le commerçant gestionnaire de ce commerce");
      }
      return;
    }

    if (authenticatedUserId == null || !authenticatedUserId.equals(store.getOwnerUid())) {
      throw new AccessDeniedException("Accès refusé pour la mise à jour du stock");
    }
  }
}
