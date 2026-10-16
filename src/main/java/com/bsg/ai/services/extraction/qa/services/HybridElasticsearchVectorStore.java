package com.bsg.ai.services.extraction.qa.services;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnRetriever;
import co.elastic.clients.elasticsearch._types.Retriever;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchAiSearchFilterExpressionConverter;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Extends {@link ElasticsearchVectorStore} with hybrid search: combines kNN (dense vector)
 * and BM25 full-text search using Elasticsearch's Reciprocal Rank Fusion (RRF) via the
 * Retriever API (replaces the deprecated top-level {@code rank} + {@code knn} approach).
 *
 * <p>Query structure sent to Elasticsearch:
 * <pre>
 * retriever:
 *   rrf:
 *     retrievers:
 *       - knn  (dense vector search on textVector field)
 *       - standard (BM25 match query on content field)
 *     rank_window_size: &lt;configurable&gt;
 *     rank_constant:    &lt;configurable&gt;
 * </pre>
 *
 * <p>Tuning knobs:
 * <ul>
 *   <li>{@code rankWindowSize} – candidate docs each retriever contributes to the RRF
 *       merge window (higher = more recall, default 100)</li>
 *   <li>{@code rankConstant} – smoothing constant (default 60, rarely needs changing)</li>
 *   <li>{@code fuzzyBm25} – apply {@code fuzziness: AUTO} on the BM25 match query
 *       to tolerate typos (default false)</li>
 *   <li>{@code minScore} – minimum raw RRF score to include a hit; see field javadoc
 *       for score-range table and tuning guide (default 0.02)</li>
 * </ul>
 *
 * <p>Requires Elasticsearch 8.9+.
 */
public class HybridElasticsearchVectorStore extends ElasticsearchVectorStore {

    private final ElasticsearchClient esClient;
    private final ElasticsearchVectorStoreOptions storeOptions;
    private final EmbeddingModel embeddingModelRef;
    private final FilterExpressionConverter filterConverter;
    private final long rankWindowSize;
    private final long rankConstant;
    private final boolean fuzzyBm25;
    /**
     * Minimum RRF score a document must reach to be included in results.
     * Operates on the <em>raw</em> RRF score returned by Elasticsearch, NOT on cosine
     * similarity or BM25 relevance directly.
     *
     * <p><b>RRF score formula</b> (per document):
     * <pre>
     *   score = Σ  1 / (rankConstant + rank_i)
     * </pre>
     * where {@code rank_i} is the document's 1-based rank inside sub-retriever {@code i}.
     *
     * <p><b>Score ranges</b> with default config ({@code rankConstant=60, rankWindowSize=100}):
     * <pre>
     *   Source              | Example ranks          | RRF score
     *   --------------------|------------------------|-----------------------------
     *   Both (kNN + BM25)   | rank  1 in both        | 2/61  ≈ 0.0328   ← best
     *   Both (kNN + BM25)   | rank 10 in both        | 2/70  ≈ 0.0286
     *   Both (kNN + BM25)   | rank 40 in both        | 2/100 = 0.0200   ← boundary at default threshold
     *   Both (kNN + BM25)   | rank 41 in both        | 2/101 ≈ 0.0198   ← filtered out at default threshold
     *   Both (mixed ranks)  | rank 1 kNN + rank 100  | 1/61+1/160≈0.023 ← passes at default threshold
     *   kNN  only           | rank  1 in kNN         | 1/61  ≈ 0.0164   ← always filtered at default threshold
     *   BM25 only           | rank  1 in BM25        | 1/61  ≈ 0.0164   ← always filtered at default threshold
     * </pre>
     *
     * <p><b>Key implication of the threshold (0.02):</b><br>
     * The maximum score a document can achieve from a <em>single</em> retriever is
     * {@code 1 / (rankConstant + 1) = 1/61 ≈ 0.0164}, which is below 0.02.
     * A document found by only one retriever (kNN-only OR BM25-only) is therefore
     * <em>always</em> filtered out regardless of its rank.
     * Effectively, the default threshold enforces cross-validation: a document must
     * appear in <b>both</b> the kNN and BM25 result sets, and their combined ranks
     * must be ≤ ~40 each.
     *
     * <p><b>Normalisation note:</b><br>
     * After retrieval, {@code hit.score()} (the RRF score) is passed through
     * {@code normalizeScore()}, which applies the cosine formula {@code (2 * score) - 1}.
     * That formula is designed for cosine similarity scores in [0, 1], not for RRF scores.
     * Treat the resulting value as a relative ranking signal, not a true cosine similarity.
     *
     * <p><b>Tuning guide:</b>
     * <ul>
     *   <li>Raise threshold → higher precision, fewer results, stricter cross-validation.</li>
     *   <li>Lower threshold (e.g. below {@code 1/(rankConstant+1)}) → includes
     *       single-retriever hits, higher recall, may introduce noise.</li>
     *   <li>When {@code rankConstant} changes, recalculate:
     *       {@code minBothRetrieverThreshold = 2 / (rankConstant + maxRank)}</li>
     * </ul>
     */
    private final double minScore;

    protected HybridElasticsearchVectorStore(HybridBuilder builder) {
        super(builder);
        this.storeOptions      = builder.storeOptions;
        this.rankWindowSize    = builder.rankWindowSize;
        this.rankConstant      = builder.rankConstant;
        this.fuzzyBm25         = builder.fuzzyBm25;
        this.minScore          = builder.minScore;
        this.embeddingModelRef = builder.getEmbeddingModel();
        this.filterConverter   = new ElasticsearchAiSearchFilterExpressionConverter();
        // getNativeClient() is safe to call after super() completes
        this.esClient = (ElasticsearchClient) getNativeClient()
                .orElseThrow(() -> new IllegalStateException("ElasticsearchClient not available"));
    }

    /**
     * Hybrid kNN + BM25 search with RRF re-ranking computed in Java (basic-licence compatible).
     * Runs two separate Elasticsearch queries and fuses results using:
     * <pre>  rrfScore = Σ  1 / (rankConstant + rank_i)</pre>
     */
    @Override
    public List<Document> doSimilaritySearch(SearchRequest searchRequest) {
        return this.doSimilaritySearchUsingCustomRRF(searchRequest);
    }

    public List<Document> doSimilaritySearchUsingCustomRRF(SearchRequest searchRequest) {
        Assert.notNull(searchRequest, "SearchRequest must not be null");
        try {
            float[] vectors = this.embeddingModelRef.embed(searchRequest.getQuery());
            String filterQuery = toFilterQueryString(searchRequest);
            int windowSize = (int) rankWindowSize;

            // 1. kNN search
            var knnHits = this.esClient.search(sr -> sr
                            .index(storeOptions.getIndexName())
                            .retriever(buildKnnRetriever(vectors, searchRequest.getTopK(), filterQuery))
                            .size(windowSize),
                    Document.class).hits().hits();

            // 2. BM25 search
            var bm25Hits = this.esClient.search(sr -> sr
                            .index(storeOptions.getIndexName())
                            .retriever(buildStandardRetriever(searchRequest.getQuery(), filterQuery))
                            .size(windowSize),
                    Document.class).hits().hits();

            // 3. RRF fusion in Java
            Map<String, Double> rrfScores = new HashMap<>();
            Map<String, Document> docById  = new HashMap<>();

            accumulateRrf(knnHits, rrfScores, docById);
            accumulateRrf(bm25Hits, rrfScores, docById);

            // 4. Filter by minScore, sort descending, take topK
            return rrfScores.entrySet().stream()
                    .filter(e -> e.getValue() >= minScore)
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .limit(searchRequest.getTopK())
                    .map(e -> {
                        double normalized = normalizeScore(e.getValue());
                        return docById.get(e.getKey()).mutate()
                                .metadata(DocumentMetadata.DISTANCE.value(), 1 - normalized)
                                .score(normalized)
                                .build();
                    })
                    .sorted(Comparator.comparingDouble((Document d) -> d.getScore() != null ? d.getScore() : 0.0)
                            .reversed()
                            .thenComparing(Comparator.comparing(Document::getId).reversed()))
                    .collect(Collectors.toList());

        } catch (IOException e) {
            throw new RuntimeException("Hybrid search failed", e);
        }
    }

    // This is the paid feature of Elasticsearch. We have implemented exactly same feature in custom RRF.
    // This is just for reference. And the test case is available for the same to test the similarity between both the implementations.
    public List<Document> doSimilaritySearchUsingInbuiltRRF(SearchRequest searchRequest) {
        Assert.notNull(searchRequest, "SearchRequest must not be null");
        try {
            float[] vectors = this.embeddingModelRef.embed(searchRequest.getQuery());
            String filterQuery = toFilterQueryString(searchRequest);

            var response = this.esClient.search(sr -> sr
                            .index(storeOptions.getIndexName())
                            .retriever(r -> r.rrf(rrf -> rrf
                                    .retrievers(
                                            buildKnnRetriever(vectors, searchRequest.getTopK(), filterQuery),
                                            buildStandardRetriever(searchRequest.getQuery(), filterQuery))
                                    .rankWindowSize((int) rankWindowSize)
                                    .rankConstant((int) rankConstant)))
                            .size(searchRequest.getTopK()),
                    Document.class);

            return response.hits().hits().stream()
                    .filter(hit -> hit.source() != null && hit.score() != null && hit.score() >= minScore)
                    .map(hit -> {
                        Document doc = hit.source();
                        double normalized = normalizeScore(hit.score());
                        return doc.mutate()
                                .metadata(DocumentMetadata.DISTANCE.value(), 1 - normalized)
                                .score(normalized)
                                .build();
                    })
                    .sorted(Comparator.comparingDouble((Document d) -> d.getScore() != null ? d.getScore() : 0.0)
                            .reversed()
                            .thenComparing(Comparator.comparing(Document::getId).reversed()))
                    .collect(Collectors.toList());

        } catch (IOException e) {
            throw new RuntimeException("Hybrid search failed", e);
        }
    }

    private <T> void accumulateRrf(List<co.elastic.clients.elasticsearch.core.search.Hit<T>> hits,
                                    Map<String, Double> rrfScores,
                                    Map<String, Document> docById) {
        for (int i = 0; i < hits.size(); i++) {
            var hit = hits.get(i);
            rrfScores.merge(hit.id(), 1.0 / (rankConstant + i + 1), Double::sum);
            if (hit.source() instanceof Document doc) {
                docById.putIfAbsent(hit.id(), doc);
            }
        }
    }

    private Retriever buildKnnRetriever(float[] vectors, int topK, String filterQuery) {
        return Retriever.of(r -> r.knn(KnnRetriever.of(knn -> knn
                .field(storeOptions.getEmbeddingFieldName())
                .queryVector(EmbeddingUtils.toList(vectors))
                .k(topK)
                .numCandidates((int) (1.5 * topK))
                .filter(Query.of(q -> q.queryString(qs -> qs.query(filterQuery)))))));
    }

    private Retriever buildStandardRetriever(String queryText, String filterQuery) {
        return Retriever.of(r -> r.standard(std -> std
                .query(buildBm25Query(queryText))
                .filter(Query.of(q -> q.queryString(qs -> qs.query(filterQuery))))));
    }

    private Query buildBm25Query(String queryText) {
        return Query.of(q -> q
                .match(m -> {
                    m.field("content").query(queryText);
                    if (fuzzyBm25) {
                        m.fuzziness("AUTO");
                    }
                    return m;
                }));
    }

    private String toFilterQueryString(SearchRequest searchRequest) {
        return Objects.isNull(searchRequest.getFilterExpression()) ? "*"
                : filterConverter.convertExpression(searchRequest.getFilterExpression());
    }

    /**
     * Mirrors {@code ElasticsearchVectorStore#normalizeSimilarityScore}, which is private
     * and cannot be called via super. Kept in sync manually.
     */
    private double normalizeScore(double score) {
        if (storeOptions.getSimilarity() == SimilarityFunction.l2_norm) {
            return 1 - (Math.sqrt((1 / score) - 1));
        }
        return (2 * score) - 1;
    }

    // -------------------------------------------------------------------------

    public static HybridBuilder builder(RestClient restClient, EmbeddingModel embeddingModel) {
        return new HybridBuilder(restClient, embeddingModel);
    }

    public static class HybridBuilder extends ElasticsearchVectorStore.Builder {

        private ElasticsearchVectorStoreOptions storeOptions = new ElasticsearchVectorStoreOptions();
        private long rankWindowSize  = 100L;
        private long rankConstant    = 60L;
        private boolean fuzzyBm25    = false;
        private double minScore      = 0.00;

        public HybridBuilder(RestClient restClient, EmbeddingModel embeddingModel) {
            super(restClient, embeddingModel);
        }

        /** Overridden to keep a local copy of options (parent field is private). */
        @Override
        public HybridBuilder options(ElasticsearchVectorStoreOptions options) {
            super.options(options);
            this.storeOptions = options;
            return this;
        }

        /** Overridden to preserve HybridBuilder return type in the fluent chain. */
        @Override
        public HybridBuilder initializeSchema(boolean initializeSchema) {
            super.initializeSchema(initializeSchema);
            return this;
        }

        /**
         * Controls how many candidate docs each retriever contributes to the RRF merge
         * window. Higher values increase recall at the cost of latency. Defaults to 100.
         */
        public HybridBuilder rankWindowSize(long rankWindowSize) {
            this.rankWindowSize = rankWindowSize;
            return this;
        }

        /**
         * RRF smoothing constant. The default of 60 is well-established empirically
         * and rarely needs tuning.
         */
        public HybridBuilder rankConstant(long rankConstant) {
            this.rankConstant = rankConstant;
            return this;
        }

        /**
         * When {@code true}, the BM25 match query uses {@code fuzziness: AUTO},
         * which tolerates typos in the query text. Defaults to {@code false}.
         */
        public HybridBuilder fuzzyBm25(boolean fuzzyBm25) {
            this.fuzzyBm25 = fuzzyBm25;
            return this;
        }

        /**
         * Minimum RRF score threshold. See {@link HybridElasticsearchVectorStore#minScore}
         * for full documentation on score ranges and tuning guidance. Defaults to {@code 0.02}.
         */
        public HybridBuilder minScore(double minScore) {
            this.minScore = minScore;
            return this;
        }

        @Override
        public HybridElasticsearchVectorStore build() {
            return new HybridElasticsearchVectorStore(this);
        }
    }
}
