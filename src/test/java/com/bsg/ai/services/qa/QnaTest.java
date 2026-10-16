package com.bsg.ai.services.qa;

import com.bsg.ai.services.DocumentUploadControllerTest;
import com.bsg.ai.services.config.OcrMockConfiguration;
import com.bsg.ai.services.extraction.services.service.DocumentProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OcrMockConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public class QnaTest {

    private static final String QNA_URL = "/document-question-answer/ask";

    private static final boolean FORCE_UPLOAD = false;

    private static final boolean USE_CSV_FORMAT = false;
    public static final double TEST_CASE_PASSING_THRESHOLD = 0.6;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @Autowired
    private ElasticsearchVectorStore vectorStore;

    private static final List<TestResult> RESULTS = new ArrayList<>();

    static Map<String, List<String>> PDF_PATH_QUESTION_SET_MAP = Map.of(
            "static/qna/HR-Policy.pdf", List.of("static/qna/question_set/HR-Policy.json"),
            "static/qna/HR-Policy-Manual-Template.pdf", List.of("static/qna/question_set/HR-Policy-Manual-Template.json"),
            "static/qna/Betriebsvereinbarung_zu_SAP_ERP_HR.pdf", List.of("static/qna/question_set/Betriebsvereinbarung_zu_SAP_ERP_HR.json",
                    "static/qna/question_set/Betriebsvereinbarung_zu_SAP_ERP_HR-ENG.json")
    );

    static Stream<TestCase> testCases() {
        return PDF_PATH_QUESTION_SET_MAP.entrySet().stream()
                .flatMap(entry -> {
                    String path = entry.getKey();
                    String documentId = getDocumentId(path);
                    return entry.getValue().stream().flatMap(questionSetPath -> QnaTestDataLoader.load(questionSetPath)
                            .map(tc -> new TestCase(tc.id(), tc.question(), tc.expectedInAnswer(), documentId)));
                });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testCases")
    void runQnaTest(TestCase tc) throws Exception {

        String requestBody = """
                {"question": "%s", "documentId": "%s"}
                """.formatted(tc.question(), tc.documentId());

        String response = mockMvc.perform(post(QNA_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String normalized = response.toLowerCase().replaceAll("[^a-z0-9 ]", "");
        long matched = tc.expectedInAnswer().stream()
                .filter(k -> normalized.toLowerCase().contains(k.toLowerCase()))
                .count();

        float score = (float) matched / tc.expectedInAnswer().size();
        boolean passed = score >= TEST_CASE_PASSING_THRESHOLD;

        RESULTS.add(new TestResult(tc.id(), tc.question(), tc.expectedInAnswer(), response, passed, score));

        assertThat(response).isNotBlank();

        assertThat(passed)
                .as("Test failed for question: %s", tc.question())
                .isTrue();
    }

    // ---------------------------------------------------------
    // Document ingestion (dynamic PDF)
    // ---------------------------------------------------------

    private static String getDocumentId(String path) {
        return path.replaceAll("[^a-zA-Z0-9]", "_");
    }

    /**
     * Ensures the test document is available in Elasticsearch before any Q&A test runs.
     * Respects {@link #FORCE_UPLOAD}: when {@code true}, existing chunks are removed
     * and the document is re-ingested from scratch.
     */
    @BeforeAll
    void ensureDocumentIngested() {
        PDF_PATH_QUESTION_SET_MAP.keySet().forEach(path -> {
            String documentId = getDocumentId(path);
            boolean existsInStore = documentExistsInStore(documentId);

            if (existsInStore && FORCE_UPLOAD) {
                log.info("[QnaTestRunner] FORCE_UPLOAD=true — removing existing chunks for '{}'", documentId);
                removeDocumentFromStore(documentId);
                existsInStore = false;
            }

            if (existsInStore) {
                log.info("[QnAControllerTest] Document '{}' already present in ES — skipping ingestion.", documentId);
            } else {
                log.info("[QnAControllerTest] Ingesting '{}' into ES — this may take a while for a large PDF.", documentId);
                try {
                    ingestDocument(path);
                } catch (Exception e) {
                    log.error("error while ingesting document for path: {}", path);
                }
                log.info("[QnAControllerTest] Ingestion complete for '{}'.", documentId);
            }
        });
    }

    // ---------------------------------------------------------
    // Report generation
    // ---------------------------------------------------------

    @AfterAll
    static void generateReport() throws Exception {
        QnaReportWriter.write(RESULTS, USE_CSV_FORMAT);
    }

    private boolean documentExistsInStore(String documentId) {
        List<?> hits = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("spring framework")
                        .filterExpression("documentId == '" + documentId + "'")
                        .topK(1)
                        .similarityThreshold(0.0)
                        .build()
        );
        return !hits.isEmpty();
    }

    private void removeDocumentFromStore(String documentId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression byDocumentId = b.eq("documentId", documentId).build();
        vectorStore.delete(byDocumentId);
        log.info("[QnAControllerTest] Deleted chunks for '{}' from ES.", documentId);
    }

    /**
     * Reads the test PDF from the classpath and calls
     * {@link DocumentProcessingService#ingestDocumentWithSemanticStitching} directly (bypasses the HTTP
     * layer for setup — the upload controller is tested separately in
     * {@link DocumentUploadControllerTest}).
     */
    private void ingestDocument(String path) throws Exception {
        byte[] pdfBytes = new ClassPathResource(path)
                .getInputStream()
                .readAllBytes();

        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "HR-Policy.pdf",
                "application/pdf",
                pdfBytes
        );

        documentProcessingService.ingestDocumentWithSemanticStitching(pdfFile, getDocumentId(path));
    }
}
