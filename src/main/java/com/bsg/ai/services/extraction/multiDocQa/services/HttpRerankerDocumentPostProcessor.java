package com.bsg.ai.services.extraction.multiDocQa.services;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.bsg.ai.services.extraction.multiDocQa.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.Query;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reranker that calls a TEI (Text Embeddings Inference) / vLLM-compatible
 * rerank REST endpoint:
 *   POST /rerank
 *   { "model": "...", "query": "...", "texts": ["doc1", "doc2", ...] }
 *
 * Spring AI's DocumentPostProcessor interface is used so this reranker plugs
 * directly into the RetrievalAugmentationAdvisor pipeline.
 *
 * Compatible services:
 *   - Hugging Face Text Embeddings Inference (TEI) with a cross-encoder model
 *   - vLLM rerank endpoint
 *   - Jina AI Reranker API (change base URL + add API key header)
 *   - Cohere Rerank API (adapt payload shape)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpRerankerDocumentPostProcessor implements DocumentPostProcessor {

    private final RagProperties ragProperties;
    private volatile RestClient restClient;

    // ── REST client is built lazily to pick up final property values ─────────
    private RestClient restClient() {
        if (restClient == null) {
            synchronized (this) {
                if (restClient == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(5_000);   // 5 seconds
                    factory.setReadTimeout(30_000);      // 30 seconds

                    restClient = RestClient.builder()
                            .baseUrl(ragProperties.reranker().baseUrl())
                            .requestFactory(factory)
                            .build();
                }
            }
        }
        return restClient;
    }

    /**
     * Reranks {@code documents} against the original {@code query} text.
     * The top-N documents (configured via rag.reranker.top-n) are returned
     * ordered by descending relevance score.
     */
    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            log.warn("No documents retrieved for query: {}", query.text());
            return documents;
        }

        int topN = ragProperties.reranker().topN();
        if (documents.size() <= topN) {
            return documents;
        }
        String model = ragProperties.reranker().model();

        List<String> queries = (List<String>) query.context().getOrDefault("rag_queries", new ArrayList<>());

        String queryText;
        if (queries.isEmpty()) {
            queryText = extractQuery(query.text());
        } else {
            queryText = queries.stream()
                    .distinct()
                    .limit(5)
                    .collect(Collectors.joining(" "));
        }

        log.debug("Reranking {} documents for query '{}' using model '{}'", documents.size(), queryText, model);

        for (Document doc : documents) {
            if (!doc.getMetadata().containsKey("retriever_score")) {
                doc.getMetadata().put("retriever_score", 0.0);
            }
        }

        List<String> texts = documents.stream()
                .map(doc -> {
                    String docId = String.valueOf(doc.getMetadata().getOrDefault("documentId", "unknown"));
                    String section = String.valueOf(doc.getMetadata().getOrDefault("section", ""));

                    return "[docId: " + docId + "]\n"
                            + "[section: " + section + "]\n"
                            + doc.getText();
                })
                .toList();

        RerankResponse response;
        try {
            response = restClient().post()
                    .uri("/rerank")
                    .body(new RerankRequest(model, queryText, texts))
                    .retrieve()
                    .body(RerankResponse.class);
        } catch (Exception ex) {
            log.warn("Reranker call failed, falling back to retriever ranking. Cause: {}", ex.toString(), ex);

            return documents.stream()
                    .sorted(Comparator.comparingDouble((Document d) ->
                            (double) d.getMetadata().getOrDefault("retriever_score", 0.0)
                    ).reversed())
                    .limit(topN)
                    .toList();
        }

        if (response == null || response.results() == null || response.results().isEmpty()) {
            log.warn("Reranker returned empty results – returning original document order.");
            return documents.stream().limit(topN).toList();
        }

        List<Document> rerankedDocs = response.results().stream()
                .map(result -> {
                    Document doc = documents.get(result.index());

                    double rerankerScore = result.relevanceScore();
                    double retrieverScore = (double) doc.getMetadata().getOrDefault("retriever_score", 0.0);

                    String docId = String.valueOf(doc.getMetadata().get("documentId"));

                    long sameDocCount = documents.stream()
                            .filter(d -> docId.equals(d.getMetadata().get("documentId")))
                            .count();

                    double diversityPenalty = 1.0 / (1 + sameDocCount);

                    double finalScore = (0.6 * rerankerScore + 0.3 * retrieverScore) * diversityPenalty;

                    doc.getMetadata().put("reranker_score", rerankerScore);
                    doc.getMetadata().put("final_score", finalScore);

                    return doc;
                })
                .sorted(Comparator.comparingDouble((Document d) ->
                        (double) d.getMetadata().getOrDefault("final_score", 0.0)
                ).reversed())
                .limit(topN)
                .toList();

        log.debug("Top documents after reranking:");
        for (Document doc : rerankedDocs) {
            log.debug("docId={}, final_score={}, reranker_score={}, retriever_score={}",
                    doc.getMetadata().getOrDefault("documentId", "unknown"),
                    doc.getMetadata().getOrDefault("final_score", 0.0),
                    doc.getMetadata().getOrDefault("reranker_score", 0.0),
                    doc.getMetadata().getOrDefault("retriever_score", 0.0)
            );
        }

        log.debug("Returning {} documents after reranking", rerankedDocs.size());

        return rerankedDocs;
    }

    private static String extractQuery(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String lower = input.toLowerCase();

        boolean looksLikePrompt = lower.contains("question:");

        if (!looksLikePrompt) {
            return input; // already a query
        }

        return input.split("Question:")[1];
    }

    // ── TEI / vLLM-compatible payload types ─────────────────────────────────

    record RerankRequest(String model, String query, List<String> texts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RerankResponse(List<RerankResult> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RerankResult(int index, @JsonAlias("relevance_score") double relevanceScore) {}
}