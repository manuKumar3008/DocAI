package com.bsg.ai.services;

import com.bsg.ai.services.extraction.filter.dto.PdfData;
import com.bsg.ai.services.extraction.summary.constant.Language;
import com.bsg.ai.services.extraction.translation.service.TranslationService;
import com.bsg.ai.services.util.PdfExtractor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@SpringBootTest
@Slf4j
public class DocTranslationTest {
    private static final Path OUTPUT_FILE = Path.of("target", "doc-translation-results.txt");
    private static final boolean GENERATE_OUTPUT_FILE = true;
    static List<String> DOC_TRANSLATION_TEST_PDF_PATHS = new ArrayList<>();

    static {
        DOC_TRANSLATION_TEST_PDF_PATHS.add("static/translation/Alois_Vertrag_Alois Hinterhuber_VERTRAG_Dienstvertrag_15.12.1985.pdf");
        DOC_TRANSLATION_TEST_PDF_PATHS.add("static/translation/Arbeitszeugnis_Hans Probe_ZEUGNIS_Zeugnis_20.11.2014.pdf");
        DOC_TRANSLATION_TEST_PDF_PATHS.add("static/translation/Daniela_Zeugnis_MMA_Daniela Zimmermann_ZEUGNIS_Zeugnis_31.08.2004.pdf");
        DOC_TRANSLATION_TEST_PDF_PATHS.add("static/translation/Daniel_Befristeter Traineevertrag_Daniel Zimmermann_VERTRAG_Befristeter Trainee Vertrag_01.01.2007.pdf");
        DOC_TRANSLATION_TEST_PDF_PATHS.add("static/translation/Datenschutzerklärung_Daniela Zimmermann_DATZ_Datenschutzerklärung_16.03.2007.pdf");
        DOC_TRANSLATION_TEST_PDF_PATHS.add("static/translation/DZ_Zwischenzeugnis_Daniel Zimmermann_ZEUGNIS_Zeugnis_14.09.2024.pdf");
        DOC_TRANSLATION_TEST_PDF_PATHS.add("static/translation/Versetzung_Daniel Zimmermann_VERS_Unterrichtung Betriebsrat_01.07.2008.pdf");
        DOC_TRANSLATION_TEST_PDF_PATHS.add("static/other_format/test_jpeg.jpeg");
        DOC_TRANSLATION_TEST_PDF_PATHS.add("static/other_format/test_jpg.jpg");
        DOC_TRANSLATION_TEST_PDF_PATHS.add("static/other_format/test_png.png");
        DOC_TRANSLATION_TEST_PDF_PATHS.add("static/other_format/test_tiff.tif");
    }

    @Autowired
    private TranslationService translationService;

    @Autowired
    private PdfExtractor pdfExtractor;

    static Stream<String> getDocTranslationTestPDFPaths() {
        return DOC_TRANSLATION_TEST_PDF_PATHS.stream();
    }

    @ParameterizedTest
    @MethodSource("getDocTranslationTestPDFPaths")
    @SneakyThrows
    public void testDocumentTranslation(String path) {
        var resource = new ClassPathResource(path);
        var result = translationService.processResource(resource.getFile(), Language.ENGLISH);
        Assertions.assertNotNull(result);
        log.info("For document: {} summary is: {}", path, result);

        // Append formatted output into a file
        if (GENERATE_OUTPUT_FILE) {
            String formatted = path + ":\n" + result + ":\n\n";
            Files.writeString(
                    OUTPUT_FILE,
                    formatted,
                    StandardOpenOption.CREATE,   // create file if not exists
                    StandardOpenOption.APPEND    // append to the same file
            );
        }
    }

    @ParameterizedTest
    @MethodSource("getDocTranslationTestPDFPaths")
    @SneakyThrows
    public void testDocumentTranslationWithOcr(String path) {
        var resource = new ClassPathResource(path);
        var result = translationService.processResourceWithOcr(null, resource, Language.GERMAN);
        Assertions.assertNotNull(result);
    }

    @ParameterizedTest
    @MethodSource("getDocTranslationTestPDFPaths")
    @SneakyThrows
    public void testDocumentTranslationWithOcrPaginated(String path) {
        var resource = new ClassPathResource(path);
        String baseFileName = FilenameUtils.getBaseName(path);
        PdfData pdfData = pdfExtractor.extract(resource);
        for (int page = 0; page < pdfData.getPages(); page++) {
            var result = translationService.processResourceWithOcr(null, resource, Language.ENGLISH, page);
            Assertions.assertNotNull(result);
            Path filePath = Paths.get("./src/test/resources/static/translation/", page + "_" + baseFileName + ".html");
            Files.writeString(filePath, result);
        }

    }

}
