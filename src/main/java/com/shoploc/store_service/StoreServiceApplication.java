package com.shoploc.store_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableCaching
@SpringBootApplication
@ConfigurationPropertiesScan
public class StoreServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(StoreServiceApplication.class, args);
  }
}
