package com.bsg.ai.services.extraction.multiDocQa.services;


import lombok.Builder;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Fixes query mismatch between retrieval and reranking.
 * <p>
 * By default, RewriteQueryTransformer updates the query only for retrieval,
 * while post-processors (e.g., reranker) still receive the original query.
 * This can lead to poor reranking since documents were fetched using a
 * different (rewritten) query.
 * <p>
 * This wrapper stores the rewritten query in context so downstream
 * components can use the same query, ensuring consistent and accurate scoring.
 */

@Builder
public class StoringRewriteQueryTransformer implements QueryTransformer {

    private final QueryTransformer delegate;

    public StoringRewriteQueryTransformer(QueryTransformer delegate) {
        this.delegate = delegate;
    }

    @Override
    public Query transform(Query query) {
        Query transformed = delegate.transform(query);
        transformed.context().put("rewritten_query", transformed.text());
        List<String> queries = new ArrayList<>();
        queries.add(query.text());              // original
        queries.add(transformed.text());        // rewritten

        transformed.context().put("rag_queries", queries);
        return transformed;
    }
}
