package com.bsg.ai.services.extraction.multiDocQa;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Strongly-typed binding for all rag.* properties in application.yml.
 *
 * Uses constructor binding (Java records) — no Lombok needed, no annotation
 * processor required. Registered via @EnableConfigurationProperties in RagConfig.
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(Retriever retriever, Reranker reranker, Query query, Context context) {
    public RagProperties(@DefaultValue Retriever retriever, @DefaultValue Reranker reranker,
                         @DefaultValue Query query, @DefaultValue Context context) {
        this.retriever  = retriever  != null ? retriever  : new Retriever(10, 0.40);
        this.reranker   = reranker   != null ? reranker   : new Reranker(5, "BAAI/bge-reranker-v2-m3", "http://localhost:8001");
        this.query      = query      != null ? query      : new Query(true,3, true);
        this.context    = context    != null ? context    : new Context(80, 14, 2200);
    }

    public record Retriever(@DefaultValue("10") int topK, @DefaultValue("0.40") double similarityThreshold) {}

    public record Reranker(@DefaultValue("5") int topN, @DefaultValue("BAAI/bge-reranker-v2-m3") String model,
            @DefaultValue("http://localhost:8001") String baseUrl) {}

    public record Query(@DefaultValue("true") boolean expandEnabled, @DefaultValue("3") int expandCount, @DefaultValue("true") boolean rewriteEnabled) {}

    public record Context(
            @DefaultValue("80") int multiDocTopK,
            @DefaultValue("14") int maxContextChunks,
            @DefaultValue("2200") int maxCharsPerChunk) {}
}
