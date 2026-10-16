package com.bsg.ai.services;

import com.bsg.ai.services.config.OcrMockConfiguration;
import com.bsg.ai.services.extraction.services.service.DocumentProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Service-level integration tests for {@link DocumentProcessingService#ingestDocumentWithSemanticStitching}.
 *
 * <p>No mocks, no HTTP layer — calls the service directly and asserts against the real
 * Elasticsearch vector store. OCR services are mocked via {@link OcrMockConfiguration}
 * because the test PDF is a digital document and OCR output is irrelevant here.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(OcrMockConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public class DocumentUploadControllerTest {

    private static final String TEST_DOCUMENT_ID = "upload-test-spring-framework-1";
     private static final String TEST_PDF_PATH = "static/qna/spring-framework-6.0.8.pdf";
//    private static final String TEST_PDF_PATH = "static/classification_sample_Hans_Prob.pdf";

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @Autowired
    private ElasticsearchVectorStore vectorStore;

    // -------------------------------------------------------------------------
    // TC-U01: Valid PDF — chunks generated and indexed in ES
    // -------------------------------------------------------------------------

    @Test
    void givenValidPdf_whenIngestDocument_thenChunksStoredInESWithCorrectMetadata() throws Exception {
        var fileResource = new ClassPathResource(TEST_PDF_PATH);
        byte[] pdfBytes = fileResource.getInputStream().readAllBytes();
        var originalFileName = fileResource.getFilename();
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", originalFileName, "application/pdf", pdfBytes);

        String returnedId = documentProcessingService.ingestDocumentWithSemanticStitching(pdfFile, TEST_DOCUMENT_ID);

        // Returned value must equal the documentId that was passed in
        assertThat(returnedId).isEqualTo(TEST_DOCUMENT_ID);

        // Query ES to confirm chunks were indexed
        List<Document> stored = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("spring framework")
                        .filterExpression("documentId == '" + TEST_DOCUMENT_ID + "'")
                        .topK(10)
                        .similarityThreshold(0.0)
                        .build()
        );

        log.info("TC-U01: {} chunk(s) indexed in ES for documentId '{}'", stored.size(), TEST_DOCUMENT_ID);

        assertThat(stored)
                .as("At least one chunk must be present in ES after ingestion")
                .isNotEmpty();

        // Every chunk must carry the correct documentId and sourceFile in its metadata
        assertThat(stored).allSatisfy(doc -> {
            assertThat(doc.getMetadata())
                    .as("Chunk metadata must contain 'documentId'")
                    .containsEntry("documentId", TEST_DOCUMENT_ID);
            assertThat(doc.getMetadata())
                    .as("Chunk metadata must contain 'sourceFile'")
                    .containsKey("sourceFile");
        });
    }

    // -------------------------------------------------------------------------
    // Cleanup — remove test chunks from ES after all tests in this class run
    // -------------------------------------------------------------------------

    @AfterAll
    void cleanUpTestDocument() {
        log.info("[DocumentUploadControllerTest] Starting cleanup of ES chunks for '{}' in @AfterAll.", TEST_DOCUMENT_ID);
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression byDocumentId = b.eq("documentId", TEST_DOCUMENT_ID).build();
        vectorStore.delete(byDocumentId);
        log.info("[DocumentUploadControllerTest] Cleaned up ES chunks for '{}'.", TEST_DOCUMENT_ID);
    }
}
