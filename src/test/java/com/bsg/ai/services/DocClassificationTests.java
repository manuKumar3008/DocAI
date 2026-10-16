package com.bsg.ai.services;

import com.bsg.ai.services.extraction.classification.service.ClassificationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
class DocClassificationTests {

    @Autowired
    private ClassificationService classificationService;

    @Test
    void testImageProcessingViaOllama() throws Exception {
        byte[] pdfBytes = new ClassPathResource("static/classification_sample_Hans_Prob.pdf")
                .getInputStream()
                .readAllBytes();
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "classification_sample_Hans_Prob.pdf",
                "application/pdf",
                pdfBytes
        );
        var result = classificationService.processResource(pdfFile, "test");
        Assertions.assertNotNull(result);
        var klassifikation = result.get("klassifikation");
        Assertions.assertEquals("Arbeitsverträge & Vereinbarungen", klassifikation.get("hauptkategorie").asText());
        Assertions.assertEquals("Arbeitsvertrag", klassifikation.get("unterkategorie").asText());
        Assertions.assertEquals("hoch", klassifikation.get("konfidenz").asText());
    }
}
