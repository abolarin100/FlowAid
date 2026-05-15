package com.flowaid.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
public class CorsConfig {

    // Injects as String[] directly — Spring splits comma-separated values
    // automatically
    @Value("${flowaid.cors.allowed-origins}")
    private String[] allowedOrigins;

    // Define which paths CORS rules apply to — mirrors the image's
    // ADMINISTRATOR_PATHS pattern
    private static final String[] CORS_PATHS = { "/api/**" };

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Set allowed origins from environment variable (e.g. your Vercel frontend URL)
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));

        // Allow all HTTP methods — matches the image's singletonList("*")
        configuration.setAllowedMethods(Collections.singletonList("*"));

        // Allow all headers — matches the image's singletonList("*")
        configuration.setAllowedHeaders(Collections.singletonList("*"));

        // Required when frontend sends cookies or Authorization headers
        configuration.setAllowCredentials(true);

        // Cache preflight response for 1 hour to reduce OPTIONS requests
        configuration.setMaxAge(3600L);

        // Register the configuration against each defined path
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        Arrays.stream(CORS_PATHS)
                .forEach(path -> source.registerCorsConfiguration(path, configuration));

        return source;
    }
}