package com.example.hrms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Configuration
 * Note: CORS is now handled by SecurityConfig for Spring Security integration
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowed;

    // CORS is now configured in SecurityConfig to work properly with Spring Security
    // The corsConfigurationSource bean has been moved there
}
