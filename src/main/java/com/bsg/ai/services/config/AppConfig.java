package com.bsg.ai.services.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AppConfig {

    @Bean
     public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean(name = "pdfReaderExecutor", destroyMethod = "shutdown")
    public ExecutorService pdfReaderExecutor(
            @Value("${ingestion.reader-threads:4}") int threads) {
        return Executors.newFixedThreadPool(threads);
    }

    @Bean(name = "chunkingExecutor", destroyMethod = "shutdown")
    public ExecutorService chunkingExecutor(
            @Value("${ingestion.chunking-threads:6}") int threads) {
        return Executors.newFixedThreadPool(threads);
    }
}