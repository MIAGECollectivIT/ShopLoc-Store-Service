package com.shoploc.store_service.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "services")
public record ServicesProperties(UsersService users) {

  public record UsersService(@NotBlank String url) {}
}
