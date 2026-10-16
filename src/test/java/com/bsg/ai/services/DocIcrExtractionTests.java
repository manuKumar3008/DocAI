package com.bsg.ai.services;

import com.bsg.ai.services.extraction.icrform.service.IcrExtractionService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

@SpringBootTest
class DocIcrExtractionTests {

    @Autowired
    private IcrExtractionService icrExtractionService;

    @Test
    void testIncCharactorRecognitionAndExtractionTest1() throws Exception {
        var resource = new ClassPathResource("static/icr_sample_1.pdf");
        var result = icrExtractionService.processResource(resource);
        Assertions.assertNotNull(result);
        var formData = result.get("form_data");
        Assertions.assertEquals("006238", formData.get("employer_code").get("value").asText());
    }

    @Test
    void testIncCharactorRecognitionAndExtractionTest2() throws Exception {
        var resource = new ClassPathResource("static/icr_sample_2.pdf");
        var result = icrExtractionService.processResource(resource);
        Assertions.assertNotNull(result);
        var formData = result.get("form_data");
        Assertions.assertEquals("303090", formData.get("employer_code").get("value").asText());
    }
}
