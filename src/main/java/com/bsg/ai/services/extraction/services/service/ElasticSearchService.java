package com.bsg.ai.services.extraction.services.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.util.NamedValue;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ElasticSearchService {
    private final ElasticsearchClient elasticsearchClient;

    @Value("${spring.ai.vectorstore.elasticsearch.index-name:custom-index-v1}")
    private String indexName;

    protected ElasticSearchService(ElasticsearchVectorStore vectorStore) {
        Optional<ElasticsearchClient> nativeClient = vectorStore.getNativeClient();
        if (nativeClient.isPresent()) {
            this.elasticsearchClient = nativeClient.get();
        } else {
            throw new IllegalArgumentException("ElasticsearchClient not found");
        }
    }

    public List<Document> getDocumentById(String documentId) {
        if (StringUtils.isEmpty(documentId)) {
            return null;
        }
        try {
            SearchResponse<Document> response = elasticsearchClient.search(sr -> sr
                            .index(indexName)
                            // documentId is mapped as text in es so we need to use .keyword
                            .query(q -> q.term(t -> t.field("metadata.documentId.keyword").value(documentId))),
                    Document.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (ElasticsearchException e) {
            log.error("ERROR: {}", e.response().error().reason());
            e.response().error().rootCause().forEach(rc ->
                    log.debug("ROOT CAUSE: {}", rc.reason())
            );
            throw e;
        } catch (IOException ioException) {
            log.error("ERROR: {}", ioException.getMessage());
            return null;
        }
    }

    public List<Document> getTopNPageChunks(String documentId, int n) throws IOException {
        validate(documentId, n);
        Set<Long> pages = getPageNumbersAgg(documentId, n, true);
        return fetchChunksByPages(documentId, pages);
    }

    public List<Document> getBottomNPageChunks(String documentId, int n) throws IOException {
        validate(documentId, n);
        Set<Long> pages = getPageNumbersAgg(documentId, n, false);
        return fetchChunksByPages(documentId, pages);
    }

    public List<Document> getTopAndBottomNPageChunks(String documentId, int n) throws IOException {
        validate(documentId, n);

        Set<Long> topPages = getPageNumbersAgg(documentId, n, true);
        Set<Long> bottomPages = getPageNumbersAgg(documentId, n, false);

        Set<Long> allPages = new LinkedHashSet<>();
        allPages.addAll(topPages);
        allPages.addAll(bottomPages);

        if (allPages.isEmpty()) {
            return Collections.emptyList();
        }

        return fetchChunksByPages(documentId, allPages);
    }

    public Optional<PageBounds> getPageBounds(String documentId) throws IOException {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be empty");
        }

        SearchResponse<Void> response = elasticsearchClient.search(sr -> sr
                        .index(indexName)
                        .size(0)
                        .query(q -> q.term(t -> t
                                .field("metadata.documentId.keyword")
                                .value(documentId)))
                        .aggregations("min_start_page", a -> a.min(m -> m.field("metadata.startPage")))
                        .aggregations("max_end_page", a -> a.max(m -> m.field("metadata.endPage"))),
                Void.class
        );

        Aggregate minAgg = response.aggregations().get("min_start_page");
        Aggregate maxAgg = response.aggregations().get("max_end_page");
        if (minAgg == null || maxAgg == null || minAgg.min() == null || maxAgg.max() == null) {
            return Optional.empty();
        }

        double minStart = minAgg.min().value();
        double maxEnd = maxAgg.max().value();
        if (Double.isNaN(minStart) || Double.isNaN(maxEnd)) {
            return Optional.empty();
        }
        return Optional.of(new PageBounds((int) minStart, (int) maxEnd));
    }

    public List<Document> getChunksByStartPageAtMost(String documentId, int maxStartPage) throws IOException {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be empty");
        }
        SearchResponse<Document> response = elasticsearchClient.search(sr -> sr
                        .index(indexName)
                        .size(500)
                        .query(q -> q.bool(b -> b
                                .must(m -> m.term(t -> t
                                        .field("metadata.documentId.keyword")
                                        .value(documentId)))
                                .filter(f -> f.range(r -> r.term(t->t
                                        .field("metadata.startPage")
                                        .lte(String.valueOf(maxStartPage))))))
                        )
                        .sort(s -> s.field(f -> f.field("metadata.startPage").order(SortOrder.Asc))),
                Document.class
        );
        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<Document> getChunksByEndPageAtLeast(String documentId, int minEndPage) throws IOException {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be empty");
        }
        SearchResponse<Document> response = elasticsearchClient.search(sr -> sr
                        .index(indexName)
                        .size(500)
                        .query(q -> q.bool(b -> b
                                .must(m -> m.term(t -> t
                                        .field("metadata.documentId.keyword")
                                        .value(documentId)))
                                .filter(f -> f.range(r -> r.term(t->t
                                        .field("metadata.endPage")
                                        .gte(String.valueOf(minEndPage))))))
                        )
                        .sort(s -> s.field(f -> f.field("metadata.startPage").order(SortOrder.Asc))),
                Document.class
        );
        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<Document> getInteriorChunks(String documentId,
                                            int startPageExclusive,
                                            int endPageExclusive,
                                            int maxSize) throws IOException {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be empty");
        }
        int size = Math.max(1, Math.min(maxSize, 1000));
        SearchResponse<Document> response = elasticsearchClient.search(sr -> sr
                        .index(indexName)
                        .size(size)
                        .query(q -> q.bool(b -> b
                                .must(m -> m.term(t -> t
                                        .field("metadata.documentId.keyword")
                                        .value(documentId)))
                                .filter(f -> f
                                                .range(r -> r
                                                        .term(t -> t
                                                                .field("metadata.startPage")
                                                                .gt(String.valueOf(startPageExclusive)))
                                                )
                                        )
                                .filter(f -> f
                                        .range(r -> r
                                                .term(t->t
                                                        .field("metadata.endPage")
                                                        .lt(String.valueOf(endPageExclusive)))
                                        )))
                        )
                        // Corrected Sort Syntax:
                        .sort(s -> s.field(f -> f.field("metadata.startPage").order(SortOrder.Asc)))
                        .sort(s -> s.field(f -> f.field("metadata.endPage").order(SortOrder.Asc))),
                Document.class
        );
        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<Document> searchKeywordChunks(String documentId, String queryText, int size) throws IOException {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be empty");
        }
        if (queryText == null || queryText.isBlank()) {
            return Collections.emptyList();
        }

        int limited = Math.max(1, Math.min(size, 50));
        SearchResponse<Document> response = elasticsearchClient.search(sr -> sr
                        .index(indexName)
                        .size(limited)
                        .query(q -> q.bool(b -> b
                                .must(m -> m.term(t -> t
                                        .field("metadata.documentId.keyword")
                                        .value(documentId)))
                                .must(m -> m.simpleQueryString(sqs -> sqs
                                        .query(queryText)
                                        .fields("content"))))),
                Document.class
        );

        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .toList();
    }

    private Set<Long> getPageNumbersAgg(String documentId, int size, boolean asc)
            throws IOException {
        SearchResponse<Void> response = elasticsearchClient.search(sr -> sr
                        .index(indexName)
                        .size(0)
                        .query(q -> q.term(t -> t
                                .field("metadata.documentId.keyword")
                                .value(documentId)))
                        .aggregations("pages", a -> a
                                .terms(t -> t
                                        .field("metadata.startPage")
                                        .size(size)
                                        .order(List.of(
                                                NamedValue.of("_key", asc ? SortOrder.Asc : SortOrder.Desc)
                                        ))
                                )),
                Void.class
        );

        Aggregate pagesAgg = response.aggregations().get("pages");
        if (pagesAgg == null) return new LinkedHashSet<>();

        // Handle both long and non-long mappings
        if (pagesAgg.isLterms()) {
            return pagesAgg.lterms().buckets().array().stream()
                    .map(LongTermsBucket::key)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } else {
            return pagesAgg.sterms().buckets().array().stream()
                    .map(b -> Long.parseLong(b.key().stringValue()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private List<Document> fetchChunksByPages(String documentId, Set<Long> pages)
            throws IOException {
        if (pages.isEmpty()) return Collections.emptyList();

        List<FieldValue> pageValues = pages.stream()
                .map(FieldValue::of)
                .toList();

        List<Document> allDocuments = new ArrayList<>();
        List<FieldValue> lastSortValues = null;

        while (true) {
            final List<FieldValue> searchAfterPoint = lastSortValues;

            SearchResponse<Document> response = elasticsearchClient.search(sr -> {
                sr.index(indexName)
                        .size(100)
                        .query(q -> q.bool(b -> b
                                .must(m -> m.term(t -> t
                                        .field("metadata.documentId.keyword")
                                        .value(documentId)))
                                .filter(f -> f.terms(t -> t
                                        .field("metadata.startPage")
                                        .terms(v -> v.value(pageValues))))
                        ))
                        .sort(s -> s.field(f -> f.field("metadata.startPage").order(SortOrder.Asc)))
                        .sort(s -> s.field(f -> f.field("id.keyword").order(SortOrder.Asc)));

                if (searchAfterPoint != null && !searchAfterPoint.isEmpty()) {
                    sr.searchAfter(searchAfterPoint);
                }
                return sr;
            }, Document.class);

            List<Hit<Document>> hits = response.hits().hits();
            if (hits.isEmpty()) break;

            hits.stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .forEach(allDocuments::add);

            List<FieldValue> newSortValues = hits.get(hits.size() - 1).sort();
            // Guard: if sort values are missing or unchanged, stop to avoid infinite loop
            if (newSortValues == null || newSortValues.isEmpty()
                    || newSortValues.equals(lastSortValues)) {
                break;
            }
            lastSortValues = newSortValues;
        }

        return allDocuments;
    }

    private void validate(String documentId, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be greater than zero");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be empty");
        }
    }

    /**
     * Fetches embeddings for a list of IDs.
     * @param ids List of document IDs
     * @return Map where key is the ID and value is the list of doubles (embedding)
     */
    public Map<String, List<Double>> getEmbeddingsByIds(List<String> ids) {

        // 1. Execute the Multi-Get request
        MgetResponse<ObjectNode> response = null;
        try {
             response = elasticsearchClient.mget(m -> m
                            .index(indexName)
                            .ids(ids)
                            .source(s -> s
                                    .fields(Collections.singletonList("textVector"))
                            ),
                    ObjectNode.class
            );
        } catch (IOException e) {
            log.error("error while getting embeddings by ids", e);
            return Collections.emptyMap();
        }

        Map<String, List<Double>> resultMap = new HashMap<>();

        // 2. Parse the response
        for (MultiGetResponseItem<ObjectNode> item : response.docs()) {
            // 1. Check if the item is a valid result (not an error)
            if (item.isResult()) {
                var getResult = item.result(); // This gives you the GetResult<ObjectNode>

                if (getResult.found() && getResult.source() != null) {
                    var embeddingNode = getResult.source().get("textVector");

                    if (embeddingNode != null && embeddingNode.isArray()) {
                        List<Double> vector = new java.util.ArrayList<>();
                        embeddingNode.forEach(node -> vector.add(node.asDouble()));
                        resultMap.put(getResult.id(), vector);
                    }
                }
            } else if (item.isFailure()) {
                // Optional: Handle or log the specific failure for this ID
                log.error("Failed to fetch ID: {}", item.failure().id());
            }
        }

        return resultMap;
    }

    public record PageBounds(int firstPage, int lastPage) {}
}
