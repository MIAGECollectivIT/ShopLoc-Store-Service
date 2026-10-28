package com.shoploc.store_service.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {

    /**
     * Uploads a store image to the S3 bucket and returns the public URL.
     *
     * @param file the multipart file to upload
     * @return the accessible URL of the stored image
     */
    String uploadStoreImage(MultipartFile file);
}
