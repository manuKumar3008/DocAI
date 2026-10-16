package com.bsg.ai.services;

import com.bsg.ai.services.config.OcrMockConfiguration;
import com.bsg.ai.services.extraction.qa.services.HybridElasticsearchVectorStore;
import com.bsg.ai.services.extraction.services.service.DocumentProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
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
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for {@code POST /document-question-answer/ask-with-history}.
 *
 * <p>Verifies multi-turn conversational Q&amp;A: later questions use pronouns or
 * back-references that only resolve correctly when the model has access to the
 * prior turns' context (e.g. "What are the different scopes available for <em>those</em>?"
 * only makes sense after the model has explained beans in the preceding turn).</p>
 *
 * <h3>Conversation design</h3>
 * All turns share the same {@link #CONVERSATION_ID} generated once per test run.
 * They are executed <strong>sequentially in order</strong> inside a single {@link #givenIngestedDocument_whenAskingConversationalQuestions_thenHistoryIsPreserved()}
 * test method — parallelising them would break history semantics.
 *
 * <h3>Document lifecycle</h3>
 * Same idempotent ingestion guard as {@link QnAControllerTest}:
 * flip {@link #FORCE_UPLOAD} to {@code true} to wipe and re-ingest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OcrMockConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public class QnAWithHistoryControllerTest {

    // -------------------------------------------------------------------------
    // Configuration constants
    // -------------------------------------------------------------------------

    private static final boolean FORCE_UPLOAD = false;

    private static final String TEST_DOCUMENT_ID = "20260423_1";
    private static final String TEST_PDF_CLASSPATH = "static/qna/spring-framework-6.0.8.pdf";
    private static final String ASK_WITH_HISTORY_URL = "/document-question-answer/ask-with-history";
    private static final String ES_INDEX = "custom-index-v1";

    /** Fixed for the lifetime of this test run so all turns share the same memory window. */
    private static final String CONVERSATION_ID = UUID.randomUUID().toString();

    // -------------------------------------------------------------------------
    // Conversation turn definition
    // -------------------------------------------------------------------------

    /**
     * A single turn in the multi-turn conversation.
     *
     * @param id               short display name used in logs
     * @param question         the question sent; may reference prior turns via pronouns
     * @param expectedInAnswer keywords/phrases a correct answer must contain;
     *                         at least {@link #MIN_MATCH_PERCENT}% must be present
     *                         (matched case-insensitively)
     */
    record ConversationTurn(String id, String question, List<String> expectedInAnswer) {
        static final int MIN_MATCH_PERCENT = 70;
    }

    /**
     * Ordered conversation turns.
     *
     * <p>Each turn is intentionally written so that without conversation history the
     * back-references ("it", "those", "the previous", "the other one") would be
     * ambiguous or unanswerable, making history-awareness observable.</p>
     */
    static List<ConversationTurn> conversationTurns() {
        return List.of(

                // Turn 1 — anchor: introduce IoC and the container
                new ConversationTurn(
                        "TURN-01 | ioc-intro",
                        "What is IoC in Spring and how is it related to Dependency Injection?",
                        List.of("inversion of control", "dependency injection", "container", "IoC")
                ),

                // Turn 2 — "it" refers back to the IoC container from Turn 1
                new ConversationTurn(
                        "TURN-02 | bean-management | back-ref-to-container",
                        "How does it manage the creation and lifecycle of beans?",
                        List.of("bean", "container", "lifecycle", "create")
                ),

                // Turn 3 — "those" refers back to beans discussed in Turn 2
                new ConversationTurn(
                        "TURN-03 | bean-scopes | back-ref-to-beans",
                        "What are the different scopes available for those? List it.",
                        List.of("singleton", "prototype", "scope")
                ),

                // Turn 4 — "the default one" refers back to the default scope in Turn 3
                new ConversationTurn(
                        "TURN-04 | singleton-scope | back-ref-to-default-scope",
                        "Why singleton is a default one and what exactly does it mean?",
                        List.of("singleton", "single", "instance", "shared")
                ),

                // Turn 5 — "the other main scope" refers back to prototype mentioned in Turn 3
                new ConversationTurn(
                        "TURN-05 | prototype-scope | back-ref-to-other-scope",
                        "When would I prefer using the other scope instead a default singleton?",
                        List.of("prototype", "new", "instance")
                ),

                // Turn 6 — pivot to AOP using the accumulated context of the conversation
                new ConversationTurn(
                        "TURN-06 | aop-intro | new-topic",
                        "Apart from what we discussed, what is AOP and what problem does it solve in Spring?",
                        List.of("aspect", "cross-cutting", "AOP", "concern")
                ),

                // Turn 7 — "it" refers back to AOP from Turn 6
                new ConversationTurn(
                        "TURN-07 | advice-types | back-ref-to-aop",
                        "What are the different types of advice supported by it?",
                        List.of("around", "before", "after")
                )
        );
    }

    // -------------------------------------------------------------------------
    // Spring beans
    // -------------------------------------------------------------------------

    @Autowired
    private MockMvc mockMvc;

    /** Bound to MockMvc — no real server port needed, SSE chunks are collected in-process. */
    private WebTestClient webTestClient;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private HybridElasticsearchVectorStore vectorStore;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    // -------------------------------------------------------------------------
    // One-time document ingestion
    // -------------------------------------------------------------------------

    @BeforeAll
    void ensureDocumentIngested() throws Exception {
        webTestClient = MockMvcWebTestClient.bindTo(mockMvc)
                .responseTimeout(Duration.ofMinutes(5))
                .build();

        boolean existsInStore = documentExistsInStore();

        if (existsInStore && FORCE_UPLOAD) {
            log.info("[QnAWithHistoryControllerTest] FORCE_UPLOAD=true — removing existing chunks for '{}'", TEST_DOCUMENT_ID);
            removeDocumentFromStore();
            existsInStore = false;
        }

        if (existsInStore) {
            log.info("[QnAWithHistoryControllerTest] Document '{}' already in ES — skipping ingestion.", TEST_DOCUMENT_ID);
        } else {
            log.info("[QnAWithHistoryControllerTest] Ingesting '{}' — this may take a while for a large PDF.", TEST_DOCUMENT_ID);
            ingestDocument();
            log.info("[QnAWithHistoryControllerTest] Ingestion complete for '{}'.", TEST_DOCUMENT_ID);
        }
    }

    // -------------------------------------------------------------------------
    // Multi-turn conversation test
    // -------------------------------------------------------------------------

    /**
     * Fires each {@link ConversationTurn} sequentially with the same {@link #CONVERSATION_ID}.
     * Later turns contain pronouns and back-references that are only resolvable with
     * conversation memory intact, so a failure in a later turn indicates history is broken.
     *
     * <p>Each turn POSTs to the SSE streaming endpoint. {@link WebTestClient} collects all
     * token chunks and joins them into the full answer before the keyword assertions run.
     * Turns are executed sequentially in a loop — parallelising them would corrupt the
     * shared conversation memory window.</p>
     */
    @Test
    void givenIngestedDocument_whenAskingConversationalQuestions_thenHistoryIsPreserved() {
        for (ConversationTurn turn : conversationTurns()) {
            String requestBody = """
                    {"question": "%s", "documentId": "%s", "conversationId": "%s"}
                    """.formatted(turn.question(), TEST_DOCUMENT_ID, CONVERSATION_ID);

            // POST to the non-streaming endpoint — returns one SSE event with the full answer.
            // block() waits up to 5 minutes — the condense LLM call + RAG + generation
            // can take time on local hardware, especially for verbose answers.
            // returnResult(String.class) extracts only the SSE data field from each event,
            // so event-type metadata is stripped automatically.
            List<String> chunks = webTestClient.post()
                    .uri(ASK_WITH_HISTORY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(requestBody)
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult(String.class)
                    .getResponseBody()
                    .collectList()
                    .block(Duration.ofMinutes(5));

            String answer = chunks == null ? "" : String.join("", chunks);
            log.info("[{}] Q: {} | A: {}", turn.id(), turn.question(), answer);

            assertThat(answer)
                    .as("[%s] answer must not be blank", turn.id())
                    .isNotBlank();

            var stemmedAnswer = stemmedTokens(answer);
            var stemmedExpectedMatchedKeywords = turn.expectedInAnswer().stream().filter(
                    kw -> {
                        var stemmedExpectedKeyword = stemmedTokens(kw);
                        return stemmedExpectedKeyword.isEmpty() || stemmedAnswer.containsAll(stemmedExpectedKeyword);
                    }
            ).toList();

            double matchPercent = (stemmedExpectedMatchedKeywords.size() * 100.0) / turn.expectedInAnswer().size();

            log.info("[{}] stemmed keyword match: {}/{} ({}%) — matched: {}, missed: {}",
                    turn.id(), stemmedExpectedMatchedKeywords.size(), turn.expectedInAnswer().size(),
                    String.format("%.0f", matchPercent),
                    stemmedExpectedMatchedKeywords,
                    turn.expectedInAnswer().stream().filter(kw -> !stemmedExpectedMatchedKeywords.contains(kw)).toList());

            assertThat(matchPercent)
                    .as("[%s] answer for '%s' matched only %.0f%% (%d/%d) of expected keywords after stemming. Answer: %s",
                            turn.id(), turn.question(), matchPercent,
                            stemmedExpectedMatchedKeywords.size(), turn.expectedInAnswer().size(), answer)
                    .isGreaterThanOrEqualTo(QnAControllerTest.TestCase.MIN_MATCH_PERCENT);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean documentExistsInStore() throws IOException {
        long count = elasticsearchClient.count(c -> c
                .index(ES_INDEX)
                .query(q -> q
                        .queryString(qs -> qs
                                .query("metadata.documentId.keyword:" + TEST_DOCUMENT_ID)
                        )
                )
        ).count();
        return count > 0;
    }

    /**
     * Tokenizes {@code text} using {@link EnglishAnalyzer} (standard tokenization →
     * stop-word removal → Porter stemming) and returns the resulting token list.
     * Both the expected keywords and the actual answer are passed through this method
     * before comparison, so that e.g. "injected" and "injection" both reduce to "inject".
     */
    private static List<String> stemmedTokens(String text) {
        List<String> tokens = new ArrayList<>();
        try (EnglishAnalyzer analyzer = new EnglishAnalyzer()) {
            var ts = analyzer.tokenStream("f", text);
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) tokens.add(term.toString());
            ts.end();
            ts.close();
        } catch (IOException e) {
            // fallback: naive lowercase split (should never happen with EnglishAnalyzer)
            tokens.addAll(List.of(text.toLowerCase().split("\\s+")));
        }
        return tokens;
    }

    private void removeDocumentFromStore() {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression byDocumentId = b.eq("documentId", TEST_DOCUMENT_ID).build();
        vectorStore.delete(byDocumentId);
        log.info("[QnAWithHistoryControllerTest] Deleted chunks for '{}' from ES.", TEST_DOCUMENT_ID);
    }

    private void ingestDocument() throws Exception {
        byte[] pdfBytes = new ClassPathResource(TEST_PDF_CLASSPATH)
                .getInputStream()
                .readAllBytes();

        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "spring-framework-6.0.8.pdf",
                "application/pdf",
                pdfBytes
        );

        documentProcessingService.ingestDocumentWithSemanticStitching(pdfFile, TEST_DOCUMENT_ID);
    }
}
