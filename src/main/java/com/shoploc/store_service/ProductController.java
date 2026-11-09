package com.shoploc.store_service;

import com.shoploc.store_service.dto.ProductCreateRequest;
import com.shoploc.store_service.dto.ProductResponse;
import com.shoploc.store_service.dto.ProductUpdateRequest;
import com.shoploc.store_service.dto.StockUpdateRequest;
import com.shoploc.store_service.security.RequiresRole;
import com.shoploc.store_service.security.RoleInterceptor;
import com.shoploc.store_service.storage.ImageResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/stores/{storeId}/products")
@Tag(
    name = "Products",
    description = "Gestion du catalogue Click & Collect et des stocks (Story SLE-58)")
public class ProductController {

  private final ProductService productService;

  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  private Long parseOrResolveStoreId(String storeId) {
    if (storeId == null || storeId.isBlank()) {
      throw new com.shoploc.store_service.exception.StoreNotFoundException(storeId);
    }
    String trimmed = storeId.trim();
    try {
      return Long.parseLong(trimmed);
    } catch (NumberFormatException e) {
      return productService.resolveStoreId(trimmed);
    }
  }

  @GetMapping
  @Operation(summary = "Récupérer le catalogue de produits d'un commerce (Accès public)")
  public ResponseEntity<List<ProductResponse>> getProducts(@PathVariable String storeId) {
    Long resolvedStoreId = parseOrResolveStoreId(storeId);
    List<ProductResponse> products = productService.getProductsByStore(resolvedStoreId);
    return ResponseEntity.ok(products);
  }

  public ResponseEntity<List<ProductResponse>> getProducts(Long storeId) {
    return getProducts(String.valueOf(storeId));
  }

  @GetMapping("/{productId}")
  @Operation(summary = "Récupérer la fiche unitaire d'un produit (Accès public)")
  public ResponseEntity<ProductResponse> getProduct(
      @PathVariable String storeId, @PathVariable Long productId) {
    Long resolvedStoreId = parseOrResolveStoreId(storeId);
    ProductResponse product = productService.getProductById(resolvedStoreId, productId);
    return ResponseEntity.ok(product);
  }

  public ResponseEntity<ProductResponse> getProduct(Long storeId, Long productId) {
    return getProduct(String.valueOf(storeId), productId);
  }

  @GetMapping("/{productId}/image")
  @Operation(
      summary =
          "Récupérer l'image binaire d'un produit hébergée sur S3 (avec Cache-Control - Accès public)")
  public ResponseEntity<byte[]> getProductImage(
      @PathVariable String storeId, @PathVariable Long productId) {
    Long resolvedStoreId = parseOrResolveStoreId(storeId);
    ImageResource resource = productService.getProductImage(resolvedStoreId, productId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(resource.getContentType()))
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
        .body(resource.getBytes());
  }

  public ResponseEntity<byte[]> getProductImage(Long storeId, Long productId) {
    return getProductImage(String.valueOf(storeId), productId);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary =
          "Ajouter un produit au catalogue avec photo S3 (multipart/form-data) - Réservé aux commerçants et administrateurs")
  public ResponseEntity<ProductResponse> createProduct(
      @PathVariable String storeId,
      @Valid @RequestPart("product") ProductCreateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image,
      HttpServletRequest servletRequest) {
    Long resolvedStoreId = parseOrResolveStoreId(storeId);
    Long authenticatedDbId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    ProductResponse response =
        productService.createProduct(
            resolvedStoreId, request, image, authenticatedDbId, authenticatedRole);
    URI location = URI.create("/v1/stores/" + storeId + "/products/" + response.getId());
    return ResponseEntity.created(location).body(response);
  }

  public ResponseEntity<ProductResponse> createProduct(
      Long storeId,
      ProductCreateRequest request,
      MultipartFile image,
      HttpServletRequest servletRequest) {
    return createProduct(String.valueOf(storeId), request, image, servletRequest);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary =
          "Ajouter un produit au catalogue sans photo ou avec URL directe (application/json) - Réservé aux commerçants et administrateurs")
  public ResponseEntity<ProductResponse> createProductJson(
      @PathVariable String storeId,
      @Valid @RequestBody ProductCreateRequest request,
      HttpServletRequest servletRequest) {
    Long resolvedStoreId = parseOrResolveStoreId(storeId);
    Long authenticatedDbId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    ProductResponse response =
        productService.createProduct(
            resolvedStoreId, request, null, authenticatedDbId, authenticatedRole);
    URI location = URI.create("/v1/stores/" + storeId + "/products/" + response.getId());
    return ResponseEntity.created(location).body(response);
  }

  public ResponseEntity<ProductResponse> createProductJson(
      Long storeId, ProductCreateRequest request, HttpServletRequest servletRequest) {
    return createProductJson(String.valueOf(storeId), request, servletRequest);
  }

  @PutMapping(value = "/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary =
          "Mettre à jour un produit existant avec nouvelle photo S3 (multipart/form-data) - Réservé aux commerçants et administrateurs")
  public ResponseEntity<ProductResponse> updateProduct(
      @PathVariable String storeId,
      @PathVariable Long productId,
      @Valid @RequestPart("product") ProductUpdateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image,
      HttpServletRequest servletRequest) {
    Long resolvedStoreId = parseOrResolveStoreId(storeId);
    Long authenticatedDbId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    ProductResponse response =
        productService.updateProduct(
            resolvedStoreId, productId, request, image, authenticatedDbId, authenticatedRole);
    return ResponseEntity.ok(response);
  }

  public ResponseEntity<ProductResponse> updateProduct(
      Long storeId,
      Long productId,
      ProductUpdateRequest request,
      MultipartFile image,
      HttpServletRequest servletRequest) {
    return updateProduct(String.valueOf(storeId), productId, request, image, servletRequest);
  }

  @PutMapping(value = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary =
          "Mettre à jour un produit existant en JSON (application/json) - Réservé aux commerçants et administrateurs")
  public ResponseEntity<ProductResponse> updateProductJson(
      @PathVariable String storeId,
      @PathVariable Long productId,
      @Valid @RequestBody ProductUpdateRequest request,
      HttpServletRequest servletRequest) {
    Long resolvedStoreId = parseOrResolveStoreId(storeId);
    Long authenticatedDbId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    ProductResponse response =
        productService.updateProduct(
            resolvedStoreId, productId, request, null, authenticatedDbId, authenticatedRole);
    return ResponseEntity.ok(response);
  }

  public ResponseEntity<ProductResponse> updateProductJson(
      Long storeId,
      Long productId,
      ProductUpdateRequest request,
      HttpServletRequest servletRequest) {
    return updateProductJson(String.valueOf(storeId), productId, request, servletRequest);
  }

  @DeleteMapping("/{productId}")
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary = "Supprimer un produit du catalogue - Réservé aux commerçants et administrateurs")
  public ResponseEntity<Void> deleteProduct(
      @PathVariable String storeId,
      @PathVariable Long productId,
      HttpServletRequest servletRequest) {
    Long resolvedStoreId = parseOrResolveStoreId(storeId);
    Long authenticatedDbId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    productService.deleteProduct(resolvedStoreId, productId, authenticatedDbId, authenticatedRole);
    return ResponseEntity.noContent().build();
  }

  public ResponseEntity<Void> deleteProduct(
      Long storeId, Long productId, HttpServletRequest servletRequest) {
    return deleteProduct(String.valueOf(storeId), productId, servletRequest);
  }

  @PatchMapping("/{productId}/stock")
  @RequiresRole({"MERCHANT", "ADMIN", "SERVICE"})
  @Operation(
      summary =
          "Ajuster le stock physique disponible (delta ou valeur absolue) - Réservé aux commerçants, admins et services internes (M2M)")
  public ResponseEntity<ProductResponse> updateStock(
      @PathVariable String storeId,
      @PathVariable Long productId,
      @Valid @RequestBody StockUpdateRequest request,
      HttpServletRequest servletRequest) {
    Long resolvedStoreId = parseOrResolveStoreId(storeId);
    Long authenticatedDbId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    ProductResponse response =
        productService.updateStock(
            resolvedStoreId, productId, request, authenticatedDbId, authenticatedRole);
    return ResponseEntity.ok(response);
  }

  public ResponseEntity<ProductResponse> updateStock(
      Long storeId, Long productId, StockUpdateRequest request, HttpServletRequest servletRequest) {
    return updateStock(String.valueOf(storeId), productId, request, servletRequest);
  }

  @PatchMapping(value = "/{productId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary =
          "Mettre à jour la photo d'un produit (upload multipart vers S3) - Réservé aux commerçants et administrateurs")
  public ResponseEntity<ProductResponse> updateProductImageMultipart(
      @PathVariable String storeId,
      @PathVariable Long productId,
      @RequestPart("image") MultipartFile image,
      HttpServletRequest servletRequest) {
    Long resolvedStoreId = parseOrResolveStoreId(storeId);
    Long authenticatedDbId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    ProductResponse response =
        productService.updateProductImage(
            resolvedStoreId, productId, image, authenticatedDbId, authenticatedRole);
    return ResponseEntity.ok(response);
  }

  public ResponseEntity<ProductResponse> updateProductImageMultipart(
      Long storeId, Long productId, MultipartFile image, HttpServletRequest servletRequest) {
    return updateProductImageMultipart(String.valueOf(storeId), productId, image, servletRequest);
  }

  @PatchMapping(value = "/{productId}/image", consumes = MediaType.APPLICATION_JSON_VALUE)
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary =
          "Mettre à jour l'image d'un produit via URL JSON - Réservé aux commerçants et administrateurs")
  public ResponseEntity<ProductResponse> updateProductImageJson(
      @PathVariable String storeId,
      @PathVariable Long productId,
      @RequestBody Map<String, String> body,
      HttpServletRequest servletRequest) {
    Long resolvedStoreId = parseOrResolveStoreId(storeId);
    Long authenticatedDbId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    String imageUrl = body != null ? body.get("imageUrl") : null;
    ProductResponse response =
        productService.updateProductImageUrl(
            resolvedStoreId, productId, imageUrl, authenticatedDbId, authenticatedRole);
    return ResponseEntity.ok(response);
  }

  public ResponseEntity<ProductResponse> updateProductImageJson(
      Long storeId, Long productId, Map<String, String> body, HttpServletRequest servletRequest) {
    return updateProductImageJson(String.valueOf(storeId), productId, body, servletRequest);
  }

  private Long extractAuthenticatedUserId(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    Object dbIdObj = request.getAttribute(RoleInterceptor.ATTR_USER_DB_ID);
    if (dbIdObj instanceof Long l) {
      return l;
    }
    if (dbIdObj instanceof Number n) {
      return n.longValue();
    }
    return null;
  }

  private String extractAuthenticatedRole(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    Object roleObj = request.getAttribute(RoleInterceptor.ATTR_USER_ROLE);
    return roleObj != null ? roleObj.toString() : null;
  }
}
