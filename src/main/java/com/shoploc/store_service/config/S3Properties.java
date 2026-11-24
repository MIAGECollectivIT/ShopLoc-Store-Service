package com.shoploc.store_service.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "s3")
public record S3Properties(
    @NotBlank String endpoint,
    @NotBlank String bucketName,
    @NotBlank String productsBucketName,
    @NotBlank String accessKey,
    @NotBlank String secretKey,
    String region,
    String publicBaseUrl) {

  public S3Properties {
    if (region == null || region.isBlank()) {
      region = "us-east-1";
    }
    if (publicBaseUrl == null) {
      publicBaseUrl = "";
    }
  }
}
