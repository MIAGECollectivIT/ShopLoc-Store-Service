package com.shoploc.store_service;

import com.shoploc.store_service.security.RoleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RoleInterceptor roleInterceptor;

    public WebConfig(RoleInterceptor roleInterceptor) {
        this.roleInterceptor = roleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleInterceptor);
    }

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