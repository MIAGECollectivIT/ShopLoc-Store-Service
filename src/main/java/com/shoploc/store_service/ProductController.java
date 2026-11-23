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
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/stores/{storeUid}/products")
@Tag(
    name = "Products",
    description = "Gestion du catalogue Click & Collect et des stocks (Story SLE-58)")
public class ProductController {

  private final ProductService productService;

  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  @GetMapping
  @Operation(summary = "Récupérer le catalogue de produits d'un commerce (Accès public)")
  public ResponseEntity<List<ProductResponse>> getProducts(@PathVariable UUID storeUid) {
    List<ProductResponse> products = productService.getProductsByStore(storeUid);
    return ResponseEntity.ok(products);
  }

  @GetMapping("/{productUid}")
  @Operation(summary = "Récupérer la fiche unitaire d'un produit (Accès public)")
  public ResponseEntity<ProductResponse> getProduct(
      @PathVariable UUID storeUid, @PathVariable UUID productUid) {
    ProductResponse product = productService.getProductByUid(storeUid, productUid);
    return ResponseEntity.ok(product);
  }

  @GetMapping("/{productUid}/image")
  @Operation(
      summary =
          "Récupérer l'image binaire d'un produit hébergée sur S3 (avec Cache-Control - Accès public)")
  public ResponseEntity<byte[]> getProductImage(
      @PathVariable UUID storeUid, @PathVariable UUID productUid) {
    ImageResource resource = productService.getProductImage(storeUid, productUid);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(resource.getContentType()))
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
        .body(resource.getBytes());
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary =
          "Ajouter un produit au catalogue avec photo S3 (multipart/form-data) - Réservé aux commerçants et administrateurs")
  public ResponseEntity<ProductResponse> createProduct(
      @PathVariable UUID storeUid,
      @Valid @RequestPart("product") ProductCreateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image,
      HttpServletRequest servletRequest) {
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    ProductResponse response =
        productService.createProduct(
            storeUid, request, image, authenticatedUserId, authenticatedRole);
    URI location = URI.create("/v1/stores/" + storeUid + "/products/" + response.getUid());
    return ResponseEntity.created(location).body(response);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary =
          "Ajouter un produit au catalogue sans photo ou avec URL directe (application/json) - Réservé aux commerçants et administrateurs")
  public ResponseEntity<ProductResponse> createProductJson(
      @PathVariable UUID storeUid,
      @Valid @RequestBody ProductCreateRequest request,
      HttpServletRequest servletRequest) {
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    ProductResponse response =
        productService.createProduct(
            storeUid, request, null, authenticatedUserId, authenticatedRole);
    URI location = URI.create("/v1/stores/" + storeUid + "/products/" + response.getUid());
    return ResponseEntity.created(location).body(response);
  }

  @PutMapping(value = "/{productUid}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary =
          "Mettre à jour un produit existant avec nouvelle photo S3 (multipart/form-data) - Réservé aux commerçants et administrateurs")
  public ResponseEntity<ProductResponse> updateProduct(
      @PathVariable UUID storeUid,
      @PathVariable UUID productUid,
      @Valid @RequestPart("product") ProductUpdateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image,
      HttpServletRequest servletRequest) {
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    ProductResponse response =
        productService.updateProduct(
            storeUid, productUid, request, image, authenticatedUserId, authenticatedRole);
    return ResponseEntity.ok(response);
  }

  @PutMapping(value = "/{productUid}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary =
          "Mettre à jour un produit existant en JSON (application/json) - Réservé aux commerçants et administrateurs")
  public ResponseEntity<ProductResponse> updateProductJson(
      @PathVariable UUID storeUid,
      @PathVariable UUID productUid,
      @Valid @RequestBody ProductUpdateRequest request,
      HttpServletRequest servletRequest) {
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    ProductResponse response =
        productService.updateProduct(
            storeUid, productUid, request, null, authenticatedUserId, authenticatedRole);
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{productUid}")
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary = "Supprimer un produit du catalogue - Réservé aux commerçants et administrateurs")
  public ResponseEntity<Void> deleteProduct(
      @PathVariable UUID storeUid,
      @PathVariable UUID productUid,
      HttpServletRequest servletRequest) {
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    productService.deleteProduct(storeUid, productUid, authenticatedUserId, authenticatedRole);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{productUid}/stock")
  @RequiresRole({"MERCHANT", "ADMIN", "SERVICE"})
  @Operation(
      summary =
          "Ajuster le stock physique disponible (delta ou valeur absolue) - Réservé aux commerçants, admins et services internes (M2M)")
  public ResponseEntity<ProductResponse> updateStock(
      @PathVariable UUID storeUid,
      @PathVariable UUID productUid,
      @Valid @RequestBody StockUpdateRequest request,
      HttpServletRequest servletRequest) {
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    ProductResponse response =
        productService.updateStock(
            storeUid, productUid, request, authenticatedUserId, authenticatedRole);
    return ResponseEntity.ok(response);
  }

  @PatchMapping(value = "/{productUid}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary =
          "Mettre à jour la photo d'un produit (upload multipart vers S3) - Réservé aux commerçants et administrateurs")
  public ResponseEntity<ProductResponse> updateProductImageMultipart(
      @PathVariable UUID storeUid,
      @PathVariable UUID productUid,
      @RequestPart("image") MultipartFile image,
      HttpServletRequest servletRequest) {
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    ProductResponse response =
        productService.updateProductImage(
            storeUid, productUid, image, authenticatedUserId, authenticatedRole);
    return ResponseEntity.ok(response);
  }

  @PatchMapping(value = "/{productUid}/image", consumes = MediaType.APPLICATION_JSON_VALUE)
  @RequiresRole({"MERCHANT", "ADMIN"})
  @Operation(
      summary =
          "Mettre à jour l'image d'un produit via URL JSON - Réservé aux commerçants et administrateurs")
  public ResponseEntity<ProductResponse> updateProductImageJson(
      @PathVariable UUID storeUid,
      @PathVariable UUID productUid,
      @RequestBody Map<String, String> body,
      HttpServletRequest servletRequest) {
    UUID authenticatedUserId = extractAuthenticatedUserId(servletRequest);
    String authenticatedRole = extractAuthenticatedRole(servletRequest);

    String imageUrl = body != null ? body.get("imageUrl") : null;
    ProductResponse response =
        productService.updateProductImageUrl(
            storeUid, productUid, imageUrl, authenticatedUserId, authenticatedRole);
    return ResponseEntity.ok(response);
  }

  private UUID extractAuthenticatedUserId(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    Object userIdObj = request.getAttribute(RoleInterceptor.ATTR_USER_ID);
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

  private String extractAuthenticatedRole(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    Object roleObj = request.getAttribute(RoleInterceptor.ATTR_USER_ROLE);
    return roleObj != null ? roleObj.toString() : null;
  }
}
