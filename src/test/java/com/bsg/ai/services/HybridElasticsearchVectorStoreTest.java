package com.bsg.ai.services;

import com.bsg.ai.services.config.OcrMockConfiguration;
import com.bsg.ai.services.extraction.qa.services.HybridElasticsearchVectorStore;
import com.bsg.ai.services.extraction.services.service.DocumentProcessingService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify {@code doSimilaritySearchUsingCustomRRF} and
 * {@code doSimilaritySearchUsingInbuiltRRF} return the same documents in the same order.
 *
 * <p>Both methods implement Reciprocal Rank Fusion over the same kNN + BM25 sub-retrievers.
 * Custom RRF runs two ES queries and fuses in Java; inbuilt RRF delegates fusion to ES.
 * This test asserts the two implementations agree on ranking for a variety of queries.</p>
 *
 * <h3>TOP_K note</h3>
 * {@code TOP_K} is set equal to {@code rankWindowSize} (10) so both methods use the same
 * number of kNN candidates. The inbuilt path passes {@code topK} as the kNN sub-retriever's
 * {@code k}; the custom path always uses {@code rankWindowSize}. Keeping them equal
 * makes the comparison apples-to-apples.
 *
 * <h3>ASSERT_EXACT_ORDER flag</h3>
 * Set to {@code false} on first run to observe logged ID lists before committing to a
 * strict equality assertion. Flip to {@code true} once both lists are confirmed identical.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OcrMockConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public class HybridElasticsearchVectorStoreTest {

    // -------------------------------------------------------------------------
    // Configuration constants
    // -------------------------------------------------------------------------

    private static final String TEST_DOCUMENT_ID = "20260422_2";
    private static final String TEST_PDF_CLASSPATH = "static/qna/spring-framework-6.0.8.pdf";
    private static final String ES_INDEX = "custom-index-v1";
    private static final boolean FORCE_UPLOAD = false;

    /**
     * Must equal {@code rankWindowSize} (configured as 10 in {@code ElasticsearchVectorStoreConfig})
     * so both RRF paths consider the same number of kNN candidates.
     */
    private static final int TOP_K = 10;

    /**
     * When {@code true}: asserts both methods return identical document ID lists in the same order.
     * When {@code false}: relaxed check — every ID from custom RRF must appear in inbuilt RRF results.
     * Start with {@code false} to observe output, then flip to {@code true}.
     */
    private static final boolean ASSERT_EXACT_ORDER = true;

    /**
     * When non-empty: only cases whose {@link SearchTestCase#id()} starts with one of
     * the listed prefixes are executed. Mutually exclusive with {@link #RUN_EXCEPT}.
     */
    private static final Set<String> RUN_ONLY = Set.of();

    /**
     * When non-empty: all cases are executed <em>except</em> those whose
     * {@link SearchTestCase#id()} starts with one of the listed prefixes.
     * Mutually exclusive with {@link #RUN_ONLY}.
     */
    private static final Set<String> RUN_EXCEPT = Set.of();

    // -------------------------------------------------------------------------
    // Test case definition
    // -------------------------------------------------------------------------

    record SearchTestCase(String id, String query) {}

    static Stream<SearchTestCase> searchQueries() {
        Stream<SearchTestCase> all = Stream.of(

                new SearchTestCase(
                        "TC-Q01 | ioc-and-di | what-is-ioc-and-its-relation-to-DI",
                        "What does IoC stand for and how is it related to Dependency Injection?"),

                new SearchTestCase(
                        "TC-Q02 | bean-scopes | what-is-default-bean-scope",
                        "What is the default bean scope in Spring and what does it mean?"),

                new SearchTestCase(
                        "TC-Q03 | aop-concepts | what-is-a-pointcut",
                        "What is a pointcut in Spring AOP?"),

                new SearchTestCase(
                        "TC-Q04 | aop-advice | what-are-types-of-advice",
                        "What are the different types of advice in Spring AOP?"),

                new SearchTestCase(
                        "TC-Q05 | beanfactory-vs-appctx | difference",
                        "What is the difference between BeanFactory and ApplicationContext in Spring?"),

                new SearchTestCase(
                        "TC-Q06 | spel | what-does-spel-stand-for",
                        "What does SpEL stand for and what is it used for in Spring?"),

                new SearchTestCase(
                        "TC-Q07 | prototype-scope | what-is-prototype-scope",
                        "What is the prototype bean scope in Spring?"),

                new SearchTestCase(
                        "TC-Q08 | jakarta-ee | jakarta-namespace",
                        "What is the Jakarta EE RAR file deployment in Spring ApplicationContext?"),

                new SearchTestCase(
                        "TC-Q09 | autowiring | what-is-autowiring",
                        "What does @Autowired do in Spring?"),

                new SearchTestCase(
                        "TC-Q10 | lazy-init | what-is-lazy-init",
                        "What does it mean to mark a bean as lazy-initialized in Spring?"),

                // Document meta
                new SearchTestCase(
                        "TC-Q11 | doc-meta | who-is-the-author",
                        "Who is the author of this document?"),

                new SearchTestCase(
                        "TC-Q12 | doc-meta | total-chapters",
                        "Total how many chapters are there in this document?"),

                // Java & infrastructure requirements
                new SearchTestCase(
                        "TC-Q13 | requirements | minimum-java-version",
                        "Which minimum Java version is required for Spring Framework 6?"),

                new SearchTestCase(
                        "TC-Q14 | requirements | compatible-web-containers",
                        "Which web server containers are compatible with Spring Framework 6?"),

                // Core concepts
                new SearchTestCase(
                        "TC-Q15 | overview | core-concepts-to-learn",
                        "Which are the core concepts one must learn in Spring?"),

                new SearchTestCase(
                        "TC-Q16 | overview | what-core-concepts-explained",
                        "Which core concepts are explained in this document?"),

                // Container configuration approaches
                new SearchTestCase(
                        "TC-Q17-0 | configuration | different-approaches",
                        "Describe the different approaches to configuring the Spring container."),

                new SearchTestCase(
                        "TC-Q17-1 | configuration | different-approaches",
                        "Explain the various methods used to set up and configure the Spring container."),

                new SearchTestCase(
                        "TC-Q18 | configuration | annotation-vs-java-based-difference",
                        "What is the difference between Annotation based and Java based configuration in Spring? Provide an example if possible for both."),

                // Bean & stereotype annotations
                new SearchTestCase(
                        "TC-Q19 | annotations | bean-generation-annotations",
                        "Which are the different annotations for bean generation in annotation based configuration?"),

                new SearchTestCase(
                        "TC-Q20 | annotations | stereotype-annotations-correct-spelling",
                        "What are different stereotype annotations?"),

                new SearchTestCase(
                        "TC-Q21 | annotations | stereotype-annotations-typo-sterotype",
                        "What are different sterotype annotations?"),

                new SearchTestCase(
                        "TC-Q22 | annotations | stereotype-annotations-typo-stereo-type",
                        "What are different stereo type annotations?"),

                new SearchTestCase(
                        "TC-Q23 | annotations | meaning-of-stereotype",
                        "What do you mean by stereotype in Spring?"),

                new SearchTestCase(
                        "TC-Q24 | annotations | definition-of-stereotype",
                        "What is the definition of stereotype in Spring Framework?"),

                // Trick question — answer not present in the document
                new SearchTestCase(
                        "TC-Q25 | trick | medical-injections-not-in-doc",
                        "List all medical injection types mentioned in the document."),

                // Lifecycle
                new SearchTestCase(
                        "TC-Q26 | lifecycle | bean-lifecycle-description",
                        "Describe the lifecycle discussed in the document."),

                // Resource abstraction
                new SearchTestCase(
                        "TC-Q27 | resources | image-resource-in-package",
                        "If I have an image resource in the package and want to use it. Which Spring classes are useful for the same?"),

                new SearchTestCase(
                        "TC-Q28 | resources | image-resource-outside-package",
                        "If I have an image resource outside the package on the filesystem and want to use it. Which Spring classes are useful for the same?")
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
    // Spring beans
    // -------------------------------------------------------------------------

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
        boolean existsInStore = documentExistsInStore();

        if (existsInStore && FORCE_UPLOAD) {
            log.info("[HybridVectorStoreTest] FORCE_UPLOAD=true — removing existing chunks for '{}'", TEST_DOCUMENT_ID);
            removeDocumentFromStore();
            existsInStore = false;
        }

        if (existsInStore) {
            log.info("[HybridVectorStoreTest] Document '{}' already present in ES — skipping ingestion.", TEST_DOCUMENT_ID);
        } else {
            log.info("[HybridVectorStoreTest] Ingesting '{}' into ES — this may take a while.", TEST_DOCUMENT_ID);
            ingestDocument();
            log.info("[HybridVectorStoreTest] Ingestion complete for '{}'.", TEST_DOCUMENT_ID);
        }
    }

    // -------------------------------------------------------------------------
    // Parameterised RRF comparison test
    // -------------------------------------------------------------------------

    /**
     * For each query: calls both RRF methods with the same {@link SearchRequest} and
     * asserts the returned document ID lists match (order-sensitive when
     * {@link #ASSERT_EXACT_ORDER} is {@code true}).
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("searchQueries")
    void givenIngestedDocument_whenSearchWithBothRrfMethods_thenDocumentOrderMatches(SearchTestCase tc) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression docFilter = b.eq("documentId", TEST_DOCUMENT_ID).build();

        SearchRequest request = SearchRequest.builder()
                .query(tc.query())
                .topK(TOP_K)
                .filterExpression(docFilter)
                .build();

        List<Document> customResults  = vectorStore.doSimilaritySearchUsingCustomRRF(request);
        List<Document> inbuiltResults = vectorStore.doSimilaritySearchUsingInbuiltRRF(request);

        List<String> customIds  = extractIds(customResults);
        List<String> inbuiltIds = extractIds(inbuiltResults);

        logWithScores(tc.id(), "custom ", customResults);
        logWithScores(tc.id(), "inbuilt", inbuiltResults);
        logMismatches(tc.id(), customResults, inbuiltResults);

        assertThat(customIds)
                .as("[%s] customRRF returned no documents for query: '%s'", tc.id(), tc.query())
                .isNotEmpty();
        assertThat(inbuiltIds)
                .as("[%s] inbuiltRRF returned no documents for query: '%s'", tc.id(), tc.query())
                .isNotEmpty();

        if (ASSERT_EXACT_ORDER) {
            assertThat(customIds)
                    .as("[%s] Document ID lists must match in size and order.\n  custom : %s\n  inbuilt: %s\n  query  : '%s'",
                            tc.id(), customIds, inbuiltIds, tc.query())
                    .isEqualTo(inbuiltIds);
        } else {
            assertThat(inbuiltIds)
                    .as("[%s] All IDs from customRRF must appear in inbuiltRRF results.\n  custom : %s\n  inbuilt: %s",
                            tc.id(), customIds, inbuiltIds)
                    .containsAll(customIds);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<String> extractIds(List<Document> docs) {
        return docs.stream()
                   .map(Document::getId)
                   .collect(Collectors.toList());
    }

    private void logWithScores(String tcId, String label, List<Document> docs) {
        String formatted = IntStream.range(0, docs.size())
                .mapToObj(i -> docs.get(i).getId() + "(score=" + formatScore(docs.get(i).getScore()) + ")")
                .collect(Collectors.joining(", "));
        log.info("[{}] {} ({} docs): [{}]", tcId, label, docs.size(), formatted);
    }

    private void logMismatches(String tcId, List<Document> custom, List<Document> inbuilt) {
        int maxSize = Math.max(custom.size(), inbuilt.size());
        for (int i = 0; i < maxSize; i++) {
            Document c = i < custom.size()  ? custom.get(i)  : null;
            Document n = i < inbuilt.size() ? inbuilt.get(i) : null;
            boolean match = c != null && n != null && c.getId().equals(n.getId());
            if (!match) {
                String cStr = c != null ? c.getId() + "(score=" + formatScore(c.getScore()) + ")" : "<missing>";
                String nStr = n != null ? n.getId() + "(score=" + formatScore(n.getScore()) + ")" : "<missing>";
                log.info("[{}] MISMATCH pos {}: custom={} | inbuilt={}", tcId, i, cStr, nStr);
            }
        }
    }

    private String formatScore(Double score) {
        return score != null ? String.format("%.6f", score) : "null";
    }

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

    private void removeDocumentFromStore() {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression byDocumentId = b.eq("documentId", TEST_DOCUMENT_ID).build();
        vectorStore.delete(byDocumentId);
        log.info("[HybridVectorStoreTest] Deleted chunks for '{}' from ES.", TEST_DOCUMENT_ID);
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
