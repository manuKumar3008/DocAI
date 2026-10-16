package com.bsg.ai.services;

import com.bsg.ai.services.extraction.summary.constant.Language;
import com.bsg.ai.services.extraction.summary.constant.SummaryType;
import com.bsg.ai.services.extraction.summary.service.OcrSummaryService;
import com.bsg.ai.services.extraction.summary.service.SummaryService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@SpringBootTest
@Slf4j
public class DocSummaryTests {

    @Autowired
    private SummaryService docSummaryService;

    @Autowired
    private OcrSummaryService ocrSummaryService;

    static List<String> DOC_SUMMARY_TEST_PDF_PATHS = new ArrayList<>();

    static {
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/Alois_Vertrag_Alois Hinterhuber_VERTRAG_Dienstvertrag_15.12.1985.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/Arbeitszeugnis_Hans Probe_ZEUGNIS_Zeugnis_20.11.2014.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/Daniela_Zeugnis_MMA_Daniela Zimmermann_ZEUGNIS_Zeugnis_31.08.2004.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/Daniel_Befristeter Traineevertrag_Daniel Zimmermann_VERTRAG_Befristeter Trainee Vertrag_01.01.2007.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/Datenschutzerklärung_Daniela Zimmermann_DATZ_Datenschutzerklärung_16.03.2007.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/DZ_Zwischenzeugnis_Daniel Zimmermann_ZEUGNIS_Zeugnis_14.09.2024.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/Versetzung_Daniel Zimmermann_VERS_Unterrichtung Betriebsrat_01.07.2008.pdf");

        // BEWERBUNG
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/BEWERBUNG/ADP_Rademacher_Anschreiben_Johannes-Dietrich Rademacher-Schumann_BEWERBUNG_Anschreiben_01.01.1990.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/BEWERBUNG/AH_Bewerbung komplett_Alois Hinterhuber_BEWERBUNG_Bewerbungsunterlagen_01.07.1985.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/BEWERBUNG/Bewerbung IT Spezialist_Daniel Zimmermann_BEWERBUNG_Anschreiben_01.12.2018.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/BEWERBUNG/Bewerbungsunterlagen Organisationsprogrammierer_Franz Winter_BEWERBUNG_Bewerbungsunterlagen_24.04.2015.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/BEWERBUNG/Bewerbungsunterlagen Organisationsprogrammierer_Sarah Sommer_BEWERBUNG_Bewerbungsunterlagen_26.04.2015.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/BEWERBUNG/DZ_Bewerbung_Anschreiben_Daniel Zimmermann_BEWERBUNG_Bewerbungsunterlagen_17.07.2008.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/BEWERBUNG/DZ_Bewerbung_Daniela Zimmermann_BEWERBUNG_Bewerbungsunterlagen_14.07.2008.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/BEWERBUNG/HP_Bewerbungsunterlagen_Hans Probe_BEWERBUNG_Bewerbungsunterlagen_02.12.2014.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/BEWERBUNG/LL_Bewerbungsunterlagen_Lara Lohmann_BEWERBUNG_Bewerbungsunterlagen_02.04.2015.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/BEWERBUNG/Laura_Bewerbungsunterlagen_Laura Lange_BEWERBUNG_Bewerbungsunterlagen_02.04.2012.pdf");
        // VERTRAG
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/VERTRAG/Alois_Vertrag_Alois Hinterhuber_VERTRAG_Dienstvertrag_15.12.1985.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/VERTRAG/Daniel_Befristeter Traineevertrag_Daniel Zimmermann_VERTRAG_Befristeter Trainee Vertrag_01.01.2007.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/VERTRAG/Daniela_Anstellungsvertrag_Daniela Zimmermann_Vertrag_Anstellungsvertrag_01.01.2008.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/VERTRAG/Hans_Arbeitsvertrag_Hans Probe_VERTRAG_Anstellungsvertrag_08.05.2015.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/VERTRAG/Helmut_Arbeitsvertrag_Helmut Meier_VERTRAG_Anstellungsvertrag_20.08.2010.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/VERTRAG/Lara_Arbeitsvertrag_Lara Lohmann_VERTRAG_Anstellungsvertrag_08.05.2015.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/mistral_llm_test_docs/VERTRAG/Laura_Arbeitsvertrag_Laura Lange_VERTRAG_Anstellungsvertrag_08.05.2012.pdf");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/other_format/test_jpeg.jpeg");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/other_format/test_jpg.jpg");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/other_format/test_png.png");
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/other_format/test_tiff.tif");
    }

    private static final Path OUTPUT_FILE = Path.of("target", "doc-summary-results.txt");
    private static final boolean GENERATE_OUTPUT_FILE = false;

    @ParameterizedTest
    @MethodSource("getDocSummeryTestPDFPaths")
    @SneakyThrows
    public void testDocumentSummarization(String path) {
        var resource = new ClassPathResource(path);
        var result = docSummaryService.processResource(resource.getFile(), SummaryType.SHORT);
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
    @MethodSource("getDocSummeryTestPDFPaths")
    @SneakyThrows
    public void testDocumentSummarizationWithOcrService(String path) {
        var resource = new ClassPathResource(path);
        var result = ocrSummaryService.processPdfWithOcr(resource, Language.ENGLISH);
        Assertions.assertNotNull(result);
        log.info("For document: {} summary is: {}", path, result);
    }

    @ParameterizedTest
    @MethodSource("getDocSummeryTestPDFPaths")
    @SneakyThrows
    public void testDocumentSummarizationWithOcrServiceSingleShot(String path) {
        var resource = new ClassPathResource(path);
        var result = ocrSummaryService.processPdfWithOcrInOneShot(resource, Language.ENGLISH);
        Assertions.assertNotNull(result);
        log.info("For document: {} summary is: {}", path, result);
    }


    @ParameterizedTest
    @MethodSource("getDocSummeryTestPDFPaths")
    @SneakyThrows
    public void testDocumentSummarizationWithOcr(String path) {
        var resource = new ClassPathResource(path);
        var result = docSummaryService.processResourceWithOcr(resource, SummaryType.SHORT, "1", Language.ENGLISH);
        Assertions.assertNotNull(result);
//        log.info("For document: {} summary is: {}", path, result);
    }

    static Stream<String> getDocSummeryTestPDFPaths() {
        return DOC_SUMMARY_TEST_PDF_PATHS.stream();
    }
}
