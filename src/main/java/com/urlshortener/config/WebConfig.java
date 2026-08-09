package com.urlshortener.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global Cross-Origin Resource Sharing (CORS) Configuration.
 * 
 * What is CORS?
 * CORS (Cross-Origin Resource Sharing) is a browser security mechanism that restricts HTTP requests
 * made from scripts running on one origin (e.g. http://localhost:5500) to a different origin (http://localhost:8080).
 * 
 * Why do we need it?
 * Our HTML/JS frontend runs on a local web server (e.g., Live Server on port 5500 or plain file/http),
 * while our Spring Boot backend runs on port 8080. Enabling CORS allows browser Javascript fetch() calls to succeed.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
