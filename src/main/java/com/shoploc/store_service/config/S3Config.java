package com.shoploc.store_service.config;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class S3Config {

  private final S3Properties properties;

  public S3Config(S3Properties properties) {
    this.properties = properties;
  }

  @Bean
  @ConditionalOnMissingBean(S3Client.class)
  @ConditionalOnProperty(name = "s3.endpoint")
  public S3Client s3Client() {
    AwsBasicCredentials credentials =
        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey());

    return S3Client.builder()
        .endpointOverride(URI.create(properties.endpoint()))
        .credentialsProvider(StaticCredentialsProvider.create(credentials))
        .region(Region.of(properties.region()))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
  }
}
