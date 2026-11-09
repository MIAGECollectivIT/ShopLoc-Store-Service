package com.shoploc.store_service;

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
import com.shoploc.store_service.exception.StoreNotFoundException;
import com.shoploc.store_service.storage.ImageResource;
import com.shoploc.store_service.storage.ImageStorageService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductService {

  private static final Logger log = LoggerFactory.getLogger(ProductService.class);

  private final ProductRepository productRepository;
  private final StoreRepository storeRepository;
  private final ImageStorageService imageStorageService;

  public ProductService(
      ProductRepository productRepository,
      StoreRepository storeRepository,
      ImageStorageService imageStorageService) {
    this.productRepository = productRepository;
    this.storeRepository = storeRepository;
    this.imageStorageService = imageStorageService;
  }

  @Transactional(readOnly = true)
  public List<ProductResponse> getProductsByStore(Long storeId) {
    verifyStoreExists(storeId);
    return productRepository.findByStoreId(storeId).stream()
        .map(ProductResponse::fromEntity)
        .toList();
  }

  @Transactional(readOnly = true)
  public ProductResponse getProductById(Long storeId, Long productId) {
    verifyStoreExists(storeId);
    Product product =
        productRepository
            .findByIdAndStoreId(productId, storeId)
            .orElseThrow(() -> new ProductNotFoundException(storeId, productId));
    return ProductResponse.fromEntity(product);
  }

  @Transactional
  public ProductResponse createProduct(
      Long storeId,
      ProductCreateRequest request,
      MultipartFile image,
      Long authenticatedUserId,
      String userRole) {
    Store store = findStoreOrThrow(storeId);
    validateStoreOwnership(store, authenticatedUserId, userRole);

    String normalizedRef = request.getReference().trim();
    if (productRepository.existsByStoreIdAndReference(storeId, normalizedRef)) {
      throw new ProductReferenceAlreadyExistsException(normalizedRef, storeId);
    }

    String imageUrl = request.getImageUrl();
    if (image != null && !image.isEmpty()) {
      imageUrl = imageStorageService.uploadProductImage(image);
      log.info(
          "Image uploadée avec succès pour le produit dans le magasin {} : {}", storeId, imageUrl);
    }

    Product product =
        new Product(
            storeId,
            normalizedRef,
            request.getName().trim(),
            request.getDescription(),
            request.getPrice(),
            imageUrl,
            request.getStockQuantity() != null ? request.getStockQuantity() : 0);

    Product savedProduct = productRepository.save(product);
    log.info(
        "Nouveau produit '{}' (id={}) créé avec succès dans le commerce {}",
        savedProduct.getName(),
        savedProduct.getId(),
        storeId);
    return ProductResponse.fromEntity(savedProduct);
  }

  @Transactional
  public ProductResponse updateProduct(
      Long storeId,
      Long productId,
      ProductUpdateRequest request,
      MultipartFile image,
      Long authenticatedUserId,
      String userRole) {
    Store store = findStoreOrThrow(storeId);
    validateStoreOwnership(store, authenticatedUserId, userRole);

    Product product =
        productRepository
            .findByIdAndStoreId(productId, storeId)
            .orElseThrow(() -> new ProductNotFoundException(storeId, productId));

    String normalizedRef = request.getReference().trim();
    if (productRepository.existsByStoreIdAndReferenceAndIdNot(storeId, normalizedRef, productId)) {
      throw new ProductReferenceAlreadyExistsException(normalizedRef, storeId);
    }

    product.setReference(normalizedRef);
    product.setName(request.getName().trim());
    product.setDescription(request.getDescription());
    product.setPrice(request.getPrice());

    if (request.getStockQuantity() != null) {
      product.setStockQuantity(request.getStockQuantity());
    }

    if (image != null && !image.isEmpty()) {
      String imageUrl = imageStorageService.uploadProductImage(image);
      product.setImageUrl(imageUrl);
    } else if (request.getImageUrl() != null) {
      product.setImageUrl(request.getImageUrl());
    }

    Product updatedProduct = productRepository.save(product);
    log.info(
        "Produit id={} mis à jour avec succès dans le commerce {}",
        updatedProduct.getId(),
        storeId);
    return ProductResponse.fromEntity(updatedProduct);
  }

  @Transactional
  public void deleteProduct(
      Long storeId, Long productId, Long authenticatedUserId, String userRole) {
    Store store = findStoreOrThrow(storeId);
    validateStoreOwnership(store, authenticatedUserId, userRole);

    Product product =
        productRepository
            .findByIdAndStoreId(productId, storeId)
            .orElseThrow(() -> new ProductNotFoundException(storeId, productId));

    if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
      imageStorageService.deleteImage(product.getImageUrl());
    }

    productRepository.delete(product);
    log.info("Produit id={} supprimé avec succès du commerce {}", productId, storeId);
  }

  @Transactional
  public ProductResponse updateProductImage(
      Long storeId,
      Long productId,
      MultipartFile image,
      Long authenticatedUserId,
      String userRole) {
    Store store = findStoreOrThrow(storeId);
    validateStoreOwnership(store, authenticatedUserId, userRole);

    if (image == null || image.isEmpty()) {
      throw new InvalidFileException("Le fichier image ne peut pas être vide");
    }

    Product product =
        productRepository
            .findByIdAndStoreId(productId, storeId)
            .orElseThrow(() -> new ProductNotFoundException(storeId, productId));

    if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
      imageStorageService.deleteImage(product.getImageUrl());
    }

    String newImageUrl = imageStorageService.uploadProductImage(image);
    product.setImageUrl(newImageUrl);

    Product saved = productRepository.save(product);
    log.info("Photo du produit id={} mise à jour sur S3 : {}", productId, newImageUrl);
    return ProductResponse.fromEntity(saved);
  }

  @Transactional
  public ProductResponse updateProductImageUrl(
      Long storeId, Long productId, String imageUrl, Long authenticatedUserId, String userRole) {
    Store store = findStoreOrThrow(storeId);
    validateStoreOwnership(store, authenticatedUserId, userRole);

    Product product =
        productRepository
            .findByIdAndStoreId(productId, storeId)
            .orElseThrow(() -> new ProductNotFoundException(storeId, productId));

    product.setImageUrl(imageUrl);
    Product saved = productRepository.save(product);
    log.info("URL de photo du produit id={} mise à jour : {}", productId, imageUrl);
    return ProductResponse.fromEntity(saved);
  }

  @Transactional(readOnly = true)
  public ImageResource getProductImage(Long storeId, Long productId) {
    verifyStoreExists(storeId);
    Product product =
        productRepository
            .findByIdAndStoreId(productId, storeId)
            .orElseThrow(() -> new ProductNotFoundException(storeId, productId));

    if (product.getImageUrl() == null || product.getImageUrl().isBlank()) {
      throw new ProductImageNotFoundException(storeId, productId);
    }

    return imageStorageService
        .downloadImage(product.getImageUrl())
        .orElseThrow(() -> new ProductImageNotFoundException(storeId, productId));
  }

  @Transactional
  public ProductResponse updateStock(
      Long storeId,
      Long productId,
      StockUpdateRequest request,
      Long authenticatedUserId,
      String userRole) {
    Store store = findStoreOrThrow(storeId);
    validateStockUpdateAuthorization(store, authenticatedUserId, userRole);

    Product product =
        productRepository
            .findByIdAndStoreIdForUpdate(productId, storeId)
            .orElseThrow(() -> new ProductNotFoundException(storeId, productId));

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
              + productId
              + " (actuel: "
              + currentStock
              + ", demandé: "
              + targetStock
              + ")");
    }

    product.setStockQuantity(targetStock);
    Product saved = productRepository.save(product);
    log.info(
        "Stock ajusté pour produit id={} dans magasin {} : ancien={} -> nouveau={}",
        productId,
        storeId,
        currentStock,
        targetStock);
    return ProductResponse.fromEntity(saved);
  }

  private void verifyStoreExists(Long storeId) {
    if (!storeRepository.existsById(storeId)) {
      throw new StoreNotFoundException(storeId);
    }
  }

  private Store findStoreOrThrow(Long storeId) {
    return storeRepository.findById(storeId).orElseThrow(() -> new StoreNotFoundException(storeId));
  }

  private void validateStoreOwnership(Store store, Long authenticatedUserId, String userRole) {
    if ("ADMIN".equalsIgnoreCase(userRole)) {
      return;
    }

    if ("MERCHANT".equalsIgnoreCase(userRole)) {
      if (authenticatedUserId == null || !store.getUserId().equals(authenticatedUserId)) {
        throw new AccessDeniedException(
            "Accès refusé : vous n'êtes pas le commerçant gestionnaire du magasin "
                + store.getId());
      }
      return;
    }

    if (authenticatedUserId != null && !store.getUserId().equals(authenticatedUserId)) {
      throw new AccessDeniedException("Accès refusé : propriétaire du magasin non concordant");
    }
  }

  private void validateStockUpdateAuthorization(
      Store store, Long authenticatedUserId, String userRole) {
    // SERVICE and ADMIN roles can modify stock for any store
    if ("SERVICE".equalsIgnoreCase(userRole) || "ADMIN".equalsIgnoreCase(userRole)) {
      return;
    }

    // The merchant can only modify stock for their own store
    if ("MERCHANT".equalsIgnoreCase(userRole)) {
      if (authenticatedUserId == null || !store.getUserId().equals(authenticatedUserId)) {
        throw new AccessDeniedException(
            "Accès refusé : vous n'êtes pas le commerçant gestionnaire du magasin "
                + store.getId());
      }
      return;
    }

    if (authenticatedUserId != null && !store.getUserId().equals(authenticatedUserId)) {
      throw new AccessDeniedException("Accès refusé pour la mise à jour du stock");
    }
  }
}
