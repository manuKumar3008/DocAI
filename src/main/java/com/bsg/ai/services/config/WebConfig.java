package com.bsg.ai.services.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration class for handling CORS and other web-related settings.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // allowedOriginPatterns supports wildcards (e.g. "*") even when allowCredentials=true.
    // allowedOrigins("*") with allowCredentials=true is rejected by Spring as an invalid combination.
    @Value("${cors.allowed-origin-patterns}")
    private String[] allowedOriginPatterns;

    @Value("${cors.allowed-methods}")
    private String[] allowedMethods;

    @Value("${cors.allowed-headers}")
    private String allowedHeaders;

    @Value("${cors.allow-credentials}")
    private boolean allowCredentials;

    @Value("${cors.max-age}")
    private long maxAge;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods(allowedMethods)
                .allowedHeaders(allowedHeaders)
                .allowCredentials(allowCredentials)
                .maxAge(maxAge);
    }
}

