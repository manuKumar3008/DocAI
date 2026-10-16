package com.bsg.ai.services;

import com.bsg.ai.services.config.OcrMockConfiguration;
import com.bsg.ai.services.extraction.qa.services.HybridElasticsearchVectorStore;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.EnhancedPdfDocumentReader;
import com.bsg.ai.services.ocr.service.LlmOcrService;
import com.bsg.ai.services.ocr.service.TesseractOcrService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(OcrMockConfiguration.class)
@Slf4j
public class EnhancedPdfDocumentReaderTests {

    /**
     * Each test case holds:
     *  - id                       : short identifier used in the parameterised test display name.
     *  - pdfPath                  : classpath-relative path to the PDF under test.
     *  - pageNumber               : 1-based page number passed to getPage().
     *  - expectedTextFiles        : .txt files whose content must appear in the extracted text
     *                               regardless of whether OCR/LLM services are real or mocked.
     *  - imageExpectedTextFiles   : .txt files whose content is only checked when at least one
     *                               image service is real (test.image.ocr.enable or
     *                               test.image.llm.enable is true). Skipped when both are mocked.
     */
    record TestCase(String id, String pdfPath, int pageNumber,
                    List<String> expectedTextFiles,
                    List<String> imageExpectedTextFiles) {

        /** Convenience constructor for test cases that have no image-dependent assertions. */
        TestCase(String id, String pdfPath, int pageNumber, List<String> expectedTextFiles) {
            this(id, pdfPath, pageNumber, expectedTextFiles, List.of());
        }
    }

    static Stream<TestCase> testCases() {
        return Stream.of(
                new TestCase(
                        "TC01 | german-employment-contract | plain-text-full-content-extraction",
                        "static/summary/mistral_llm_test_docs/VERTRAG/Hans_Arbeitsvertrag_Hans Probe_VERTRAG_Anstellungsvertrag_08.05.2015.pdf",
                        1,
                        List.of("static/expected/TC01_hans_arbeitsvertrag_page1.txt")
                ),
                new TestCase(
                        "TC02 | german-employment-contract | plain-text-special-accent-character-extraction",
                        "static/summary/mistral_llm_test_docs/VERTRAG/Helmut_Arbeitsvertrag_Helmut Meier_VERTRAG_Anstellungsvertrag_20.08.2010.pdf",
                        2,
                        List.of("static/expected/TC02_helmut_arbeitsvertrag_page2_special_character_accent_extraction.txt")
                ),
                new TestCase(
                        "TC03 | normal-file-with-table | table-structure-extraction",
                        "static/extraction_test_docs/data_tables_sample.pdf",
                        1,
                        List.of("static/expected/TC03_table-structure-extraction.txt")
                ),
                new TestCase(
                        "TC04 | normal-file-with-table | table-text-extraction-with-col-span",
                        "static/extraction_test_docs/data_tables_bit_complex_colspan.pdf",
                        1,
                        List.of("static/expected/TC04_data_tables_bit_complex_colspan.txt")
                ),
                new TestCase(
                        "TC05 | file-with-paper-beads | extraction-with-beads-in-appropriate-order",
                        "static/extraction_test_docs/paper_style_normal_document.pdf",
                        1,
                        List.of("static/expected/TC05_extraction_with_beads_in_appropriate_order.txt")
                ),
                new TestCase(
                        "TC06 | normal-file-with-image | extraction-text-by-pass-image",
                        "static/extraction_test_docs/normal_file_with_image.pdf",
                        3,
                        List.of("static/expected/TC06_extraction_text_by_passing_image.txt")
                ),
                new TestCase(
                        "TC07 | normal-file-with-image | extraction-text-include-image-content-at-exact-position",
                        "static/extraction_test_docs/normal_file_with_image.pdf",
                        1,
                        List.of("static/expected/TC07_extraction_image_block_content_at_exact_position_no_ocr.txt"),
                        List.of("static/expected/TC07_extraction_text_include_image_content_at_exact_position.txt")
                ),
                new TestCase(
                        "TC08 | normal-file-with-table | table-text-extraction-with-wordwrap",
                        "static/extraction_test_docs/complex-table.pdf",
                        1,
                        List.of("static/expected/TC08_complex_table.txt")
                ),
                new TestCase(
                        "TC09 | document-with-image-at-top | image-extraction-along-with-page",
                        "static/qna/spring-framework-6.0.8.pdf",
                        33,
                        List.of("static/expected/TC09_document_with_image_at_top.txt"),
                        List.of("static/expected/TC09_document_with_image_at_top_image.txt")
                )
        );
    }

    /**
     * Reads the content of a classpath .txt file and returns it as a trimmed string.
     * The file is expected to contain the exact snippet that must appear in the extracted page text.
     */
    private static String loadExpected(String classpathPath) throws IOException {
        return new String(
                new ClassPathResource(classpathPath).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        ).trim();
    }

    @Value("${test.image.ocr.enable:false}")
    private boolean ocrEnabled;

    @Value("${test.image.llm.enable:false}")
    private boolean llmEnabled;

    private final ChatClient chatClient;
    private final TesseractOcrService tesseractOcrService;
    private final LlmOcrService llmOcrService;

    @Autowired
    EnhancedPdfDocumentReaderTests(ChatClient.Builder chatClientBuilder, TesseractOcrService tesseractOcrService, LlmOcrService llmOcrService) {
        this.chatClient = chatClientBuilder.build();
        this.tesseractOcrService = tesseractOcrService;
        this.llmOcrService = llmOcrService;
    }

    // -------------------------------------------------------------------------
    // Full-document test: content of every expected .txt file must appear in the page
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "Document: {0} page: {1}")
    @MethodSource("testCases")
    void givenPdfAndPageNumber_whenGetPage_thenExpectedTextsArePresent(TestCase tc) throws IOException {
        var resource = new ClassPathResource(tc.pdfPath());
        var reader = new EnhancedPdfDocumentReader(resource, chatClient, tesseractOcrService, llmOcrService);
        Document document = reader.readPage(tc.pageNumber());
        assertFalse(document == null || document.getText() == null || document.getText().isEmpty(),
                "Expected document along with some content");

        var pageText = document.getText();
        log.debug("Page {} text for '{}': {}", tc.pageNumber(), tc, pageText);

        // Image-dependent assertions: only run when at least one real OCR service is active.
        // When both test.image.ocr.enable and test.image.llm.enable are false, these are skipped
        // because the mocked services return empty strings for image content.
        if (ocrEnabled || llmEnabled) {
            for (String expectedFile : tc.imageExpectedTextFiles()) {
                String expected = loadExpected(expectedFile);
                assertTrue(
                        pageText.contains(expected),
                        "Expected image content of «" + expectedFile + "» not found in page "
                                + tc.pageNumber() + " of " + tc.pdfPath()
                );
            }
        } else {
            // Each .txt file is loaded independently so a failure pinpoints exactly which
            // snippet is missing, rather than failing on the first unmatched string in a list.
            for (String expectedFile : tc.expectedTextFiles()) {
                String expected = loadExpected(expectedFile);
                assertTrue(
                        pageText.contains(expected),
                        "Expected content of «" + expectedFile + "» not found in page "
                        + tc.pageNumber() + " of " + tc.pdfPath()
                );
            }
        }
    }

    // -------------------------------------------------------------------------
    // Boundary test: getPage() with an out-of-range page number must throw
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "outOfRange: {0}")
    @MethodSource("testCases")
    void givenPdf_whenGetPageOutOfRange_thenThrowsIllegalArgumentException(TestCase tc) {
        var resource = new ClassPathResource(tc.pdfPath());
        var reader = new EnhancedPdfDocumentReader(resource, chatClient, tesseractOcrService, llmOcrService);

        assertThrows(
                IllegalArgumentException.class,
                () -> reader.readPage(Integer.MAX_VALUE),
                "Expected IllegalArgumentException for out-of-range page"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> reader.readPage(0),
                "Expected IllegalArgumentException for page 0"
        );
    }
}
