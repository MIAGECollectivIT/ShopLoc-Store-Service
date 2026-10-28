package com.shoploc.store_service.storage;

import com.shoploc.store_service.exception.FileStorageException;
import com.shoploc.store_service.exception.InvalidFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ImageStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private S3ImageStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new S3ImageStorageService(
                s3Client,
                "store-images",
                "http://minio:9000",
                "https://dev.shop-loc.fr/storage/store-images"
        );
    }

    @Test
    @DisplayName("Upload réussi d'une image JPEG valide")
    void shouldUploadValidJpegImage() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "store-front.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3, 4}
        );

        String url = storageService.uploadStoreImage(file);

        assertThat(url).startsWith("https://dev.shop-loc.fr/storage/store-images/");
        assertThat(url).endsWith(".jpg");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest request = captor.getValue();
        assertThat(request.bucket()).isEqualTo("store-images");
        assertThat(request.contentType()).isEqualTo("image/jpeg");
        assertThat(request.key()).endsWith(".jpg");
    }

    @Test
    @DisplayName("Upload avec fallback sur endpoint interne si publicBaseUrl est vide")
    void shouldFallbackToInternalEndpointWhenPublicBaseUrlEmpty() {
        S3ImageStorageService localService = new S3ImageStorageService(
                s3Client,
                "store-images",
                "http://minio:9000",
                ""
        );

        MockMultipartFile file = new MockMultipartFile(
                "image",
                "logo.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        String url = localService.uploadStoreImage(file);

        assertThat(url).startsWith("http://minio:9000/store-images/");
        assertThat(url).endsWith(".png");
    }

    @Test
    @DisplayName("Échoue si le fichier est nul ou vide")
    void shouldFailWhenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "image",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertThatThrownBy(() -> storageService.uploadStoreImage(emptyFile))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("vide");

        assertThatThrownBy(() -> storageService.uploadStoreImage(null))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    @DisplayName("Échoue si le format n'est pas une image supportée (ex: PDF ou texte)")
    void shouldFailWhenContentTypeIsNotSupported() {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "image",
                "document.pdf",
                "application/pdf",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> storageService.uploadStoreImage(pdfFile))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("non supporté");
    }

    @Test
    @DisplayName("Lève FileStorageException si S3 renvoie une erreur")
    void shouldThrowFileStorageExceptionWhenS3Fails() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "store.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("S3 Bucket Unreachable").build());

        assertThatThrownBy(() -> storageService.uploadStoreImage(file))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("MinIO");
    }
}
