package com.shoploc.store_service.storage;

import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {

  /**
   * Uploads a store image to the S3 bucket and returns the public URL.
   *
   * @param file the multipart file to upload
   * @return the accessible URL of the stored image
   */
  String uploadStoreImage(MultipartFile file);

  /**
   * Uploads a product image to the dedicated product-images S3 bucket and returns the public URL.
   *
   * @param file the multipart file to upload
   * @return the accessible URL of the stored image
   */
  String uploadProductImage(MultipartFile file);

  /**
   * Retrieves an image from the S3 bucket as an ImageResource.
   *
   * @param imageUrlOrKey the public URL or S3 key of the image
   * @return the image resource with bytes and content type if found
   */
  Optional<ImageResource> downloadImage(String imageUrlOrKey);

  /**
   * Deletes an image from the S3 bucket if exists.
   *
   * @param imageUrlOrKey the public URL or S3 key of the image to delete
   */
  void deleteImage(String imageUrlOrKey);
}
