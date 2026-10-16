package com.bsg.ai.services.config;

import com.bsg.ai.services.ocr.service.LlmOcrService;
import com.bsg.ai.services.ocr.service.TesseractOcrService;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class OcrMockConfiguration {

    /**
     * Provides a Mockito mock for TesseractOcrService when test.image.ocr.enable=false (or not set).
     * When test.image.ocr.enable=true, this bean is skipped and the real TesseractOcrService is used.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "test.image.ocr.enable", havingValue = "false", matchIfMissing = true)
    public TesseractOcrService tesseractOcrServiceMock() {
        return Mockito.mock(TesseractOcrService.class);
    }

    /**
     * Provides a Mockito mock for LlmOcrService when test.image.llm.enable=false (or not set).
     * When test.image.llm.enable=true, this bean is skipped and the real LlmOcrService is used.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "test.image.llm.enable", havingValue = "false", matchIfMissing = true)
    public LlmOcrService llmOcrServiceMock() {
        return Mockito.mock(LlmOcrService.class);
    }
}
