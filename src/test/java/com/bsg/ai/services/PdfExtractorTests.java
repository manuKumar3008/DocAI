package com.bsg.ai.services;

import com.bsg.ai.services.extraction.filter.dto.PdfData;
import com.bsg.ai.services.util.PdfExtractor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Slf4j
public class PdfExtractorTests {

    static List<String> DOC_TRANSLATION_TEST_PDF_PATHS = new ArrayList<>();

    static {
        DOC_TRANSLATION_TEST_PDF_PATHS.add("static/translation/Daniel_Befristeter Traineevertrag_Daniel Zimmermann_VERTRAG_Befristeter Trainee Vertrag_01.01.2007.pdf");
    }

    @Autowired
    private PdfExtractor pdfExtractor;

    static Stream<String> getDocTranslationTestPDFPaths() {
        return DOC_TRANSLATION_TEST_PDF_PATHS.stream();
    }

    @ParameterizedTest
    @MethodSource("getDocTranslationTestPDFPaths")
    @SneakyThrows
    public void testPdfExtractionByPage(String path) {
        var resource = new ClassPathResource(path);
        PdfData result = pdfExtractor.extract(resource, 10);
        assertNotNull(result);
        log.debug("PdfData is: {}", result.toString());
    }

}
