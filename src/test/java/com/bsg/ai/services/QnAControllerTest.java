package com.bsg.ai.services;

import com.bsg.ai.services.config.OcrMockConfiguration;
import com.bsg.ai.services.extraction.qa.services.HybridElasticsearchVectorStore;
import com.bsg.ai.services.extraction.services.service.DocumentProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
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
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * End-to-end integration tests for {@code POST /document-question-answer/ask}.
 *
 * <p>No mocks — every layer (controller → QnAService → ElasticsearchVectorStore → LLM)
 * runs for real. OCR services are mocked via {@link OcrMockConfiguration} because the
 * test PDF is a digital document and OCR output is irrelevant to Q&amp;A quality.</p>
 *
 * <h3>Document lifecycle</h3>
 * {@link #ensureDocumentIngested()} runs once before all test methods:
 * <ol>
 *   <li>Searches ES for {@value #TEST_DOCUMENT_ID}.</li>
 *   <li>If found and {@link #FORCE_UPLOAD} is {@code false} → skips ingestion.</li>
 *   <li>If found and {@link #FORCE_UPLOAD} is {@code true} → deletes all existing
 *       chunks for the document ID and re-ingests.</li>
 *   <li>If not found → ingests the document.</li>
 * </ol>
 *
 * <h3>Test scenarios</h3>
 * Each {@link TestCase} holds a question and one expected keyword/phrase.
 * All cases run in a single parameterised pass; a failure pinpoints exactly which
 * question produced a wrong answer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OcrMockConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public class QnAControllerTest {

    // -------------------------------------------------------------------------
    // Configuration constants
    // -------------------------------------------------------------------------

    /**
     * When {@code true}: throws an exception if the Elasticsearch index is not found.
     * When {@code false}: ignores missing index and proceeds with the test.
     */
    private static final boolean ERROR_ON_MISSING_INDEX = true;

    /**
     * When {@code true}: delete any existing chunks for {@value #TEST_DOCUMENT_ID}
     * from Elasticsearch and re-ingest the document before the test suite runs.
     * Flip to {@code true} whenever the source PDF or the chunking strategy changes.
     */
    private static final boolean FORCE_UPLOAD = false;

    /**
     * When non-empty: only test cases whose {@link TestCase#id()} starts with one of
     * the listed prefixes are executed (e.g. {@code Set.of("TC-Q01", "TC-Q17")}).
     * Mutually exclusive with {@link #RUN_EXCEPT}. Leave both empty to run every case.
     */
    private static final Set<String> RUN_ONLY = Set.of();

    /**
     * When non-empty: all test cases are executed <em>except</em> those whose
     * {@link TestCase#id()} starts with one of the listed prefixes
     * (e.g. {@code Set.of("TC-Q11", "TC-Q12")}).
     * Mutually exclusive with {@link #RUN_ONLY}. Leave both empty to run every case.
     */
    private static final Set<String> RUN_EXCEPT = Set.of();


    private static final String TEST_DOCUMENT_ID = "20260409_01";
    private static final String TEST_PDF_CLASSPATH = "static/qna/spring-framework-6.0.8.pdf";
    private static final String QNA_URL = "/document-question-answer/ask";
    private static final String ES_INDEX = "custom-index-v1";

    // -------------------------------------------------------------------------
    // Test case definition
    // -------------------------------------------------------------------------

    /**
     * @param id               short display name shown in the parameterised test output
     * @param question         the question sent to the Q&A endpoint
     * @param expectedInAnswer keywords/phrases a correct answer should convey; each entry
     *                         is stemmed via {@link EnglishAnalyzer} and its stemmed tokens
     *                         must all appear in the stemmed answer (handles plurals, tense,
     *                         and common synonyms); at least {@link #MIN_MATCH_PERCENT}%
     *                         of entries must match
     */
    record TestCase(String id, String question, List<String> expectedInAnswer) {
        static final int MIN_MATCH_PERCENT = 70;
    }

    static Stream<TestCase> testCases() {
        Stream<TestCase> all = Stream.of(

                new TestCase(
                        "TC-Q01 | ioc-and-di | what-is-ioc-and-its-relation-to-DI",
                        "What does IoC stand for and how is it related to Dependency Injection?",
                        List.of("dependency injection", "inversion of control", "IoC", "container", "control")
                ),

                new TestCase(
                        "TC-Q02 | bean-scopes | what-is-default-bean-scope",
                        "What is the default bean scope in Spring and what does it mean?",
                        List.of("singleton", "single instance", "shared", "default")
                ),

                new TestCase(
                        "TC-Q03 | aop-concepts | what-is-a-pointcut",
                        "What is a pointcut in Spring AOP?",
                        List.of("join point", "pointcut", "expression")
                ),

                new TestCase(
                        "TC-Q04 | aop-advice | what-are-types-of-advice",
                        "What are the different types of advice in Spring AOP?",
                        List.of("around", "before", "after")
                ),

                new TestCase(
                        "TC-Q05 | beanfactory-vs-appctx | difference",
                        "What is the difference between BeanFactory and ApplicationContext in Spring?",
                        List.of("ApplicationContext", "BeanFactory", "features")
                ),

                new TestCase(
                        "TC-Q06 | spel | what-does-spel-stand-for",
                        "What does SpEL stand for and what is it used for in Spring?",
                        List.of("expression language", "SpEL", "Spring")
                ),

                new TestCase(
                        "TC-Q07 | prototype-scope | what-is-prototype-scope",
                        "What is the prototype bean scope in Spring?",
                        List.of("prototype", "new", "instance", "bean")
                ),

                new TestCase(
                        "TC-Q08 | jakarta-ee | jakarta-namespace",
                        "What is the Jakarta EE RAR file deployment in Spring ApplicationContext?",
                        List.of("Jakarta", "deployment", "ApplicationContext")
                ),

                new TestCase(
                        "TC-Q09 | autowiring | what-is-autowiring",
                        "What does @Autowired do in Spring?",
                        List.of("autowired", "inject", "dependency", "annotation", "automatically")
                ),

                new TestCase(
                        "TC-Q10 | lazy-init | what-is-lazy-init",
                        "What does it mean to mark a bean as lazy-initialized in Spring?",
                        List.of("lazy", "startup", "initialized", "created", "ApplicationContext")
                ),

                // -----------------------------------------------------------------
                // Document meta
                // -----------------------------------------------------------------

                new TestCase(
                        "TC-Q11 | doc-meta | who-is-the-author",
                        "Who is the author of this document?",
                        List.of("Rod Johnson", "Juergen Hoeller")
                ),

                new TestCase(
                        "TC-Q12 | doc-meta | total-chapters",
                        "Total how many chapters are there in this document?",
                        List.of("10", "chapters", "chapter")
                ),

                // -----------------------------------------------------------------
                // Java & infrastructure requirements
                // -----------------------------------------------------------------

                new TestCase(
                        "TC-Q13 | requirements | minimum-java-version",
                        "Which minimum Java version is required for Spring Framework 6?",
                        List.of("17", "Java", "minimum", "version")
                ),

                new TestCase(
                        "TC-Q14 | requirements | compatible-web-containers",
                        "Which web server containers are compatible with Spring Framework 6?",
                        List.of("Tomcat", "Jetty", "Undertow")
                ),

                // -----------------------------------------------------------------
                // Core concepts
                // -----------------------------------------------------------------

                new TestCase(
                        "TC-Q15 | overview | core-concepts-to-learn",
                        "Which are the core concepts one must learn in Spring?",
                        List.of("IoC", "dependency injection", "container", "bean")
                ),

                new TestCase(
                        "TC-Q16 | overview | what-core-concepts-explained",
                        "Which core concepts are explained in this document?",
                        List.of("container", "IoC", "bean", "context")
                ),

                // -----------------------------------------------------------------
                // Container configuration approaches
                // -----------------------------------------------------------------

                new TestCase(
                        "TC-Q17-0 | configuration | different-approaches",
                        "Describe the different approaches to configuring the Spring container.",
                        List.of("annotation", "XML", "Java", "configuration", "@Configuration")
                ),

                new TestCase(
                        "TC-Q17-1 | configuration | different-approaches",
                        "Explain the various methods used to set up and configure the Spring container.",
                        List.of("annotation", "XML", "Java", "configuration", "@Configuration")
                ),

                new TestCase(
                        "TC-Q18 | configuration | annotation-vs-java-based-difference",
                        "What is the difference between Annotation based and Java based configuration in Spring? Provide an example if possible for both.",
                        List.of("@Bean", "@Configuration", "annotation", "Java", "@Component")
                ),

                // -----------------------------------------------------------------
                // Bean & stereotype annotations
                // -----------------------------------------------------------------

                new TestCase(
                        "TC-Q19 | annotations | bean-generation-annotations",
                        "Which are the different annotations for bean generation in annotation based configuration?",
                        List.of("@Component", "@Service", "@Repository", "@Controller", "@Bean")
                ),

                new TestCase(
                        "TC-Q20 | annotations | stereotype-annotations-correct-spelling",
                        "What are different stereotype annotations?",
                        List.of("@Repository", "@Service", "@Controller", "@Component", "stereotype")
                ),

                // Typo variant 1 — "sterotype" (missing 'e')
                new TestCase(
                        "TC-Q21 | annotations | stereotype-annotations-typo-sterotype",
                        "What are different sterotype annotations?",
                        List.of("@Service", "@Component", "@Repository", "@Controller")
                ),

                // Typo variant 2 — "stereo type" (space inserted)
                new TestCase(
                        "TC-Q22 | annotations | stereotype-annotations-typo-stereo-type",
                        "What are different stereo type annotations?",
                        List.of("@Controller", "@Service", "@Component", "@Repository")
                ),

                new TestCase(
                        "TC-Q23 | annotations | meaning-of-stereotype",
                        "What do you mean by stereotype in Spring?",
                        List.of("role", "component", "annotation", "class", "stereotype")
                ),

                new TestCase(
                        "TC-Q24 | annotations | definition-of-stereotype",
                        "What is the definition of stereotype in Spring Framework?",
                        List.of("annotation", "role", "component", "stereotype", "class")
                ),

                // -----------------------------------------------------------------
                // Trick question — answer not present in the document
                // QnAService system prompt: "If the answer is not in the context,
                // say 'I don't have enough information to answer that.'"
                // -----------------------------------------------------------------

                new TestCase(
                        "TC-Q25 | trick | medical-injections-not-in-doc",
                        "List all medical injection types mentioned in the document.",
                        List.of("don't", "information")
                ),

                // -----------------------------------------------------------------
                // Lifecycle
                // -----------------------------------------------------------------

                new TestCase(
                        "TC-Q26 | lifecycle | bean-lifecycle-description",
                        "Describe the lifecycle discussed in the document.",
                        List.of("lifecycle", "init", "destroy", "bean", "callback")
                ),

                // -----------------------------------------------------------------
                // Resource abstraction
                // -----------------------------------------------------------------

                // ClassPathResource — resource located inside the classpath / package
                new TestCase(
                        "TC-Q27 | resources | image-resource-in-package",
                        "If I have an image resource in the package and want to use it. Which Spring classes are useful for the same?",
                        List.of("ClassPathResource", "classpath", "resource")
                ),

                // FileSystemResource — resource located on the filesystem outside the classpath
                new TestCase(
                        "TC-Q28 | resources | image-resource-outside-package",
                        "If I have an image resource outside the package on the filesystem and want to use it. Which Spring classes are useful for the same?",
                        List.of("FileSystemResource", "filesystem", "resource")
                )

        );
        if (!RUN_ONLY.isEmpty() && !RUN_EXCEPT.isEmpty()) {
            throw new IllegalStateException("RUN_ONLY and RUN_EXCEPT cannot both be non-empty. Pick one.");
        }
        if (!RUN_ONLY.isEmpty()) {
            return all.filter(tc -> RUN_ONLY.stream().anyMatch(id -> tc.id().startsWith(id)));
        }
        if (!RUN_EXCEPT.isEmpty()) {
            return all.filter(tc -> RUN_EXCEPT.stream().noneMatch(id -> tc.id().startsWith(id)));
        }
        return all;
    }

    // -------------------------------------------------------------------------
    // Spring beans (all real — no mocks)
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

    /**
     * Ensures the test document is available in Elasticsearch before any Q&A test runs.
     * Also initialises {@link #webTestClient} bound to {@link #mockMvc} so tests can
     * consume the SSE streaming response without needing a real server port.
     * Respects {@link #FORCE_UPLOAD}: when {@code true}, existing chunks are removed
     * and the document is re-ingested from scratch.
     */
    @BeforeAll
    void ensureDocumentIngested() throws Exception {
        webTestClient = MockMvcWebTestClient.bindTo(mockMvc)
                .responseTimeout(Duration.ofMinutes(5))
                .build();

        boolean existsInStore = documentExistsInStore();

        if (existsInStore && FORCE_UPLOAD) {
            log.info("[QnAControllerTest] FORCE_UPLOAD=true — removing existing chunks for '{}'", TEST_DOCUMENT_ID);
            removeDocumentFromStore();
            existsInStore = false;
        }

        if (existsInStore) {
            log.info("[QnAControllerTest] Document '{}' already present in ES — skipping ingestion.", TEST_DOCUMENT_ID);
        } else {
            if (ERROR_ON_MISSING_INDEX)
                fail("[QnAControllerTest] Index " + ES_INDEX + " does not exist.");

            log.info("[QnAControllerTest] Ingesting '{}' into ES — this may take a while for a large PDF.", TEST_DOCUMENT_ID);
            ingestDocument();
            log.info("[QnAControllerTest] Ingestion complete for '{}'.", TEST_DOCUMENT_ID);
        }
    }

    // -------------------------------------------------------------------------
    // Parameterised Q&A test
    // -------------------------------------------------------------------------

    /**
     * For each {@link TestCase}: POST the question to the non-streaming endpoint, receive
     * the full answer in a single SSE event, then assert the answer is non-blank and covers
     * at least {@link TestCase#MIN_MATCH_PERCENT}% of the expected keywords/phrases.
     *
     * <p>The endpoint returns {@code text/event-stream} with a single {@code message} event
     * containing the complete LLM answer. {@link WebTestClient} reads the SSE data field
     * directly as a {@link String}.</p>
     *
     * <p>Matching is done after Porter stemming via {@link EnglishAnalyzer}: each expected
     * entry is stemmed into tokens, and all of those tokens must appear (in any order) in
     * the stemmed answer. This tolerates plurals, tense variation, and minor synonyms that
     * share a common stem (e.g. "inject" matches "injection", "injected", "injecting").</p>
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("testCases")
    void givenIngestedDocument_whenAskQuestion_thenAnswerContainsExpected(TestCase tc) {
        // POST to the non-streaming endpoint — returns one SSE event with the full answer.
        // block() waits up to 5 minutes — large LLM responses on local hardware can be slow.
        // returnResult(String.class) extracts only the SSE data field from each event,
        // so event-type metadata is stripped automatically.
        String requestBody = """
                {"question": "%s", "documentId": "%s"}
                """.formatted(tc.question(), TEST_DOCUMENT_ID);

        List<String> chunks = webTestClient.post()
                .uri(QNA_URL)
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
        log.info("[{}] Q: {} | A: {}", tc.id(), tc.question(), answer);

        assertThat(answer).as("[%s] answer must not be blank", tc.id()).isNotBlank();

        List<String> answerTokens = stemmedTokens(answer);
        List<String> matched = tc.expectedInAnswer().stream()
                .filter(kw -> {
                    List<String> kwTokens = stemmedTokens(kw);
                    return kwTokens.isEmpty() || answerTokens.containsAll(kwTokens);
                })
                .toList();
        double matchPercent = (matched.size() * 100.0) / tc.expectedInAnswer().size();

        log.info("[{}] stemmed keyword match: {}/{} ({}%) — matched: {}, missed: {}",
                tc.id(), matched.size(), tc.expectedInAnswer().size(),
                String.format("%.0f", matchPercent),
                matched,
                tc.expectedInAnswer().stream().filter(kw -> !matched.contains(kw)).toList());

        assertThat(matchPercent)
                .as("[%s] answer for '%s' matched only %.0f%% (%d/%d) of expected keywords after stemming. Answer: %s",
                        tc.id(), tc.question(), matchPercent,
                        matched.size(), tc.expectedInAnswer().size(), answer)
                .isGreaterThanOrEqualTo(TestCase.MIN_MATCH_PERCENT);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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

    /**
     * Returns {@code true} if at least one chunk for {@value #TEST_DOCUMENT_ID}
     * is already stored in Elasticsearch.
     */
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
     * Deletes every chunk that belongs to {@value #TEST_DOCUMENT_ID} from the store.
     * Uses {@link FilterExpressionBuilder} to build a typed filter expression so that
     * the call is not tied to any specific query-string syntax.
     */
    private void removeDocumentFromStore() {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression byDocumentId = b.eq("documentId", TEST_DOCUMENT_ID).build();
        vectorStore.delete(byDocumentId);
        log.info("[QnAControllerTest] Deleted chunks for '{}' from ES.", TEST_DOCUMENT_ID);
    }

    /**
     * Reads the test PDF from the classpath and calls
     * {@link DocumentProcessingService#ingestDocumentWithSemanticStitching} directly (bypasses the HTTP
     * layer for setup — the upload controller is tested separately in
     * {@link DocumentUploadControllerTest}).
     */
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
