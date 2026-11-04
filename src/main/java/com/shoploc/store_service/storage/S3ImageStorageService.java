package com.shoploc.store_service.storage;

import com.shoploc.store_service.exception.FileStorageException;
import com.shoploc.store_service.exception.InvalidFileException;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class S3ImageStorageService implements ImageStorageService {

  private static final Logger log = LoggerFactory.getLogger(S3ImageStorageService.class);

  private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/png", "image/webp",
      "image/gif", "image/svg+xml");

  private final S3Client s3Client;
  private final String storeBucketName;
  private final String productsBucketName;
  private final String endpoint;
  private final String publicBaseUrl;

  @Autowired
  public S3ImageStorageService(
      S3Client s3Client,
      @Value("${s3.bucket-name:store-images}") String storeBucketName,
      @Value("${s3.products-bucket-name:product-images}") String productsBucketName,
      @Value("${s3.endpoint:http://minio:9000}") String endpoint,
      @Value("${s3.public-base-url:}") String publicBaseUrl) {
    this.s3Client = s3Client;
    this.storeBucketName = storeBucketName;
    this.productsBucketName = (productsBucketName != null && !productsBucketName.isBlank())
        ? productsBucketName
        : "product-images";
    this.endpoint = endpoint.replaceAll("/+$", "");
    this.publicBaseUrl = (publicBaseUrl != null) ? publicBaseUrl.replaceAll("/+$", "") : "";
  }

  public S3ImageStorageService(
      S3Client s3Client,
      String storeBucketName,
      String endpoint,
      String publicBaseUrl) {
    this(s3Client, storeBucketName, "product-images", endpoint, publicBaseUrl);
  }

  @Override
  public String uploadStoreImage(MultipartFile file) {
    return uploadToBucket(file, storeBucketName);
  }

  @Override
  public String uploadProductImage(MultipartFile file) {
    return uploadToBucket(file, productsBucketName);
  }

  private String uploadToBucket(MultipartFile file, String targetBucket) {
    if (file == null || file.isEmpty()) {
      throw new InvalidFileException("Le fichier image ne peut pas être vide");
    }

    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
      throw new InvalidFileException(
          "Format de fichier non supporté ("
              + contentType
              + "). Formats acceptés : JPEG, PNG, WEBP, GIF, SVG");
    }

    String extension = extractExtension(file.getOriginalFilename(), contentType);
    String s3Key = UUID.randomUUID() + extension;

    try {
      PutObjectRequest putObjectRequest = PutObjectRequest.builder()
          .bucket(targetBucket)
          .key(s3Key)
          .contentType(contentType)
          .build();

      s3Client.putObject(
          putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
      log.info("Fichier uploadé avec succès sur S3 [bucket={}, key={}]", targetBucket, s3Key);
    } catch (IOException e) {
      throw new FileStorageException("Échec de lecture du fichier image", e);
    } catch (Exception e) {
      throw new FileStorageException("Échec de l'upload de l'image sur le stockage S3 MinIO", e);
    }

    return buildPublicUrl(targetBucket, s3Key);
  }

  @Override
  public Optional<ImageResource> downloadImage(String imageUrlOrKey) {
    if (imageUrlOrKey == null || imageUrlOrKey.isBlank()) {
      return Optional.empty();
    }

    ParsedS3Ref ref = parseS3Ref(imageUrlOrKey);
    if (ref.key().isBlank()) {
      return Optional.empty();
    }

    try {
      GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(ref.bucket()).key(ref.key()).build();
      ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
      String contentType = objectBytes.response().contentType();
      if (contentType == null || contentType.isBlank()) {
        contentType = guessContentType(ref.key());
      }
      return Optional.of(new ImageResource(objectBytes.asByteArray(), contentType));
    } catch (NoSuchKeyException e) {
      log.warn("Image introuvable sur S3 : bucket={}, key={}", ref.bucket(), ref.key());
      return Optional.empty();
    } catch (Exception e) {
      log.error(
          "Erreur lors de la récupération de l'image S3 [bucket={}, key={}] : {}",
          ref.bucket(),
          ref.key(),
          e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public void deleteImage(String imageUrlOrKey) {
    if (imageUrlOrKey == null || imageUrlOrKey.isBlank()) {
      return;
    }
    try {
      ParsedS3Ref ref = parseS3Ref(imageUrlOrKey);
      if (ref.key().isBlank()) {
        return;
      }
      DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder().bucket(ref.bucket()).key(ref.key()).build();
      s3Client.deleteObject(deleteRequest);
      log.info("Image S3 supprimée : bucket={}, key={}", ref.bucket(), ref.key());
    } catch (Exception e) {
      log.warn("Impossible de supprimer l'image S3 '{}' : {}", imageUrlOrKey, e.getMessage());
    }
  }

  private String extractExtension(String filename, String contentType) {
    if (filename != null && filename.contains(".")) {
      String rawExt = filename.substring(filename.lastIndexOf('.')).toLowerCase();
      if (rawExt.matches("^\\.[a-z0-9]{2,5}$")) {
        return rawExt;
      }
    }

    return switch (contentType.toLowerCase()) {
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      case "image/gif" -> ".gif";
      case "image/svg+xml" -> ".svg";
      default -> ".jpg";
    };
  }

  private String buildPublicUrl(String bucket, String key) {
    if (!publicBaseUrl.isBlank()) {
      return publicBaseUrl + "/" + bucket + "/" + key;
    }
    return endpoint + "/" + bucket + "/" + key;
  }

  private String guessContentType(String filename) {
    if (filename == null)
      return "image/jpeg";
    String lower = filename.toLowerCase();
    if (lower.endsWith(".png"))
      return "image/png";
    if (lower.endsWith(".webp"))
      return "image/webp";
    if (lower.endsWith(".gif"))
      return "image/gif";
    if (lower.endsWith(".svg"))
      return "image/svg+xml";
    return "image/jpeg";
  }

  private static class ParsedS3Ref {
    private final String bucket;
    private final String key;

    public ParsedS3Ref(String bucket, String key) {
      this.bucket = bucket;
      this.key = key;
    }

    public String bucket() {
      return bucket;
    }

    public String key() {
      return key;
    }
  }

  private ParsedS3Ref parseS3Ref(String input) {
    if (input == null || input.isBlank()) {
      return new ParsedS3Ref(productsBucketName, "");
    }

    String trimmed = input.trim();
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      try {
        URI uri = URI.create(trimmed);
        String path = uri.getPath();
        if (path != null) {
          if (path.startsWith("/")) {
            path = path.substring(1);
          }
          int slashIdx = path.indexOf('/');
          if (slashIdx > 0) {
            String bucket = path.substring(0, slashIdx);
            String key = path.substring(slashIdx + 1);
            return new ParsedS3Ref(bucket, key);
          }
        }
      } catch (Exception ignored) {
      }
    }

    if (trimmed.startsWith(productsBucketName + "/")) {
      return new ParsedS3Ref(
          productsBucketName, trimmed.substring(productsBucketName.length() + 1));
    }
    if (trimmed.startsWith(storeBucketName + "/")) {
      return new ParsedS3Ref(storeBucketName, trimmed.substring(storeBucketName.length() + 1));
    }

    return new ParsedS3Ref(productsBucketName, trimmed);
  }
}
