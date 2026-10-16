package com.bsg.ai.services.extraction.multiDocQa.services;

import lombok.Builder;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixes query mismatch between retrieval and reranking.
 * <p>
 * By default, MultiQueryExpander updates the query only for retrieval,
 * while post-processors (e.g., reranker) still receive the original query.
 * This can lead to poor reranking since documents were fetched using a
 * different (rewritten) query.
 * <p>
 * This wrapper stores the rewritten query in context so downstream
 * components can use the same query, ensuring consistent and accurate scoring.
 */

@Builder
public class StoringMultiQueryExpander implements QueryExpander {

    private final QueryExpander delegate;

    public StoringMultiQueryExpander(QueryExpander delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Query> expand(Query query) {
        List<Query> expandedQueries = delegate.expand(query);

        List<String> queries = new ArrayList<>();

        // Get existing queries if present
        Object existing = query.context().get("rag_queries");
        if (existing instanceof List<?>) {
            for (Object q : (List<?>) existing) {
                queries.add(String.valueOf(q));
            }
        } else {
            queries.add(query.text());
        }

        // Add expanded queries
        for (Query q : expandedQueries) {
            queries.add(q.text());
        }

        // Store back in ALL queries (important)
        for (Query q : expandedQueries) {
            q.context().put("rag_queries", queries);
        }

        return expandedQueries;
    }
}
