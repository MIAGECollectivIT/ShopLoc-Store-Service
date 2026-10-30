package com.shoploc.store_service.storage;

import com.shoploc.store_service.exception.FileStorageException;
import com.shoploc.store_service.exception.InvalidFileException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class S3ImageStorageService implements ImageStorageService {

    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/svg+xml"
    );

    private final S3Client s3Client;
    private final String bucketName;
    private final String endpoint;
    private final String publicBaseUrl;

    public S3ImageStorageService(
            S3Client s3Client,
            @Value("${s3.bucket-name:store-images}") String bucketName,
            @Value("${s3.endpoint:http://minio:9000}") String endpoint,
            @Value("${s3.public-base-url:}") String publicBaseUrl) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.publicBaseUrl = (publicBaseUrl != null) ? publicBaseUrl.replaceAll("/+$", "") : "";
    }

    @Override
    public String uploadStoreImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Le fichier image ne peut pas être vide");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidFileException(
                    "Format de fichier non supporté (" + contentType + "). Formats acceptés : JPEG, PNG, WEBP, GIF, SVG"
            );
        }

        String extension = extractExtension(file.getOriginalFilename(), contentType);
        String s3Key = UUID.randomUUID() + extension;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new FileStorageException("Échec de lecture du fichier image", e);
        } catch (Exception e) {
            throw new FileStorageException("Échec de l'upload de l'image sur le stockage S3 MinIO", e);
        }

        return buildPublicUrl(s3Key);
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

    private String buildPublicUrl(String key) {
        if (!publicBaseUrl.isBlank()) {
            return publicBaseUrl + "/" + key;
        }
        return endpoint + "/" + bucketName + "/" + key;
    }
}
