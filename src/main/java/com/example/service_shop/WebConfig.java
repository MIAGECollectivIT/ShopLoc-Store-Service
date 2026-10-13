package com.example.service_shop;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // S'applique à toutes les routes de l'API
                .allowedOrigins(
                        "http://localhost:3000", // React / Next.js
                        "http://localhost:5173", // Vite / Vue / React
                        "http://localhost:4200" // Angular
                // Tu peux aussi mettre "*" pour tout autoriser en phase de dev local
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}