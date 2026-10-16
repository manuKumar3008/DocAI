package com.bsg.ai.services.extraction.multiDocQa.services;

import com.bsg.ai.services.extraction.services.service.ElasticSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class SmartDocumentJoiner implements DocumentJoiner {

    private final int maxChunksPerDoc;
    private final int maxContextChunks;
    private final ElasticSearchService elasticSearchService;

    // MMR tuning (0.7 = more relevance, 0.3 = more diversity)
    private final double lambda = 0.7;

    public SmartDocumentJoiner(int maxContextChunks, ElasticSearchService elasticSearchService) {
        this.maxContextChunks = Math.max(1, maxContextChunks);
        this.maxChunksPerDoc = Math.max(1, Math.min(4, Math.max(1, maxContextChunks / 4)));
        this.elasticSearchService = elasticSearchService;
    }

    @Override
    public List<Document> join(Map<Query, List<List<Document>>> documentsForQuery) {

        if (documentsForQuery == null || documentsForQuery.isEmpty()) {
            return Collections.emptyList();
        }

        // ─────────────────────────────────────────────
        // 1. Flatten
        // ─────────────────────────────────────────────
        List<Document> allDocs = documentsForQuery.values().stream()
                .flatMap(List::stream)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        if (allDocs.isEmpty()) return allDocs;

        // ─────────────────────────────────────────────
        // 2. Group by documentId
        // ─────────────────────────────────────────────
        Map<String, List<Document>> groupedByDoc = allDocs.stream()
                .collect(Collectors.groupingBy(doc ->
                        String.valueOf(doc.getMetadata().getOrDefault("documentId", "unknown"))
                ));

        // Sort each doc by score
        groupedByDoc.replaceAll((docId, docs) ->
                docs.stream()
                        .sorted(Comparator.comparingDouble(this::score).reversed())
                        .toList()
        );

        // ─────────────────────────────────────────────
        // 3. Interleave (fairness baseline)
        // ─────────────────────────────────────────────
        List<Document> interleaved = new ArrayList<>();

        List<Map.Entry<String, List<Document>>> rankedDocs = groupedByDoc.entrySet().stream()
                .sorted(Comparator.comparingDouble(
                        (Map.Entry<String, List<Document>> e) -> score(e.getValue().get(0))
                ).reversed())
                .toList();

        for (int i = 0; i < maxChunksPerDoc; i++) {
            boolean added = false;

            for (Map.Entry<String, List<Document>> entry : rankedDocs) {
                List<Document> docs = entry.getValue();

                if (i < docs.size()) {
                    interleaved.add(docs.get(i));
                    added = true;

                    if (interleaved.size() >= maxContextChunks * 2) {
                        break;
                    }
                }
            }

            if (!added || interleaved.size() >= maxContextChunks * 2) break;
        }

        List<Document> uniqueDoc = deduplicate(interleaved);
        // ─────────────────────────────────────────────
        // 4. Apply MMR (diversity)
        // ─────────────────────────────────────────────
//        List<Document> finalDocs = mmrSelect(uniqueDoc, maxContextChunks);

        return uniqueDoc;
    }

    private List<Document> deduplicate(List<Document> docs) {

        List<String> candidateIds = docs.stream().map(Document::getId).toList();
        if (!candidateIds.isEmpty()) {
            Map<String, List<Double>> embeddingsByIds = elasticSearchService.getEmbeddingsByIds(candidateIds);
            if (embeddingsByIds.isEmpty()) return docs;
            else {
                for (Document candidate : docs) {
                    candidate.getMetadata().put("embedding", embeddingsByIds.get(candidate.getId()));
                }
            }
        }

        List<Document> unique = new ArrayList<>();
        Set<String> seenTexts = new HashSet<>();

        for (Document doc : docs) {

            String text = normalize(doc.getText());

            // 1. Exact / normalized duplicate
            if (seenTexts.contains(text)) continue;

            // 2. Semantic duplicate (embedding similarity)
            boolean isDuplicate = false;
            for (Document u : unique) {
                double sim = cosineSimilarity(doc, u);

                if (sim >= 0.95) { // threshold
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate) {
                unique.add(doc);
                seenTexts.add(text);
            }
        }

        return unique;
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    // ─────────────────────────────────────────────
    // MMR Selection
    // ─────────────────────────────────────────────
    private List<Document> mmrSelect(List<Document> candidates, int limit) {

//        if (limit >= candidates.size()) return candidates;
        List<String> candidateIds = candidates.stream().map(Document::getId).toList();
        if (!candidateIds.isEmpty()) {
            Map<String, List<Double>> embeddingsByIds = elasticSearchService.getEmbeddingsByIds(candidateIds);
            if (embeddingsByIds.isEmpty()) return candidates;
            else {
                for (Document candidate : candidates) {
                    candidate.getMetadata().put("embedding", embeddingsByIds.get(candidate.getId()));
                }
            }
        }
        List<Document> selected = new ArrayList<>();
        Set<Document> remaining = new HashSet<>(candidates);

        while (selected.size() < limit && !remaining.isEmpty()) {

            Document bestDoc = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (Document doc : remaining) {

                double relevance = score(doc);
                double diversityPenalty = maxSimilarity(doc, selected);

                double mmrScore = lambda * relevance - (1 - lambda) * diversityPenalty;

                if (mmrScore > bestScore) {
                    bestScore = mmrScore;
                    bestDoc = doc;
                }
            }

            if (bestDoc == null) break;

            selected.add(bestDoc);
            remaining.remove(bestDoc);
        }

        return selected;
    }

    // ─────────────────────────────────────────────
    // Similarity (cosine)
    // ─────────────────────────────────────────────
    private double maxSimilarity(Document doc, List<Document> selected) {
        if (selected.isEmpty()) return 0.0;

        double maxSim = 0.0;

        for (Document s : selected) {
            double sim = cosineSimilarity(doc, s);
            if (sim > maxSim) maxSim = sim;
        }

        return maxSim;
    }

    private double cosineSimilarity(Document d1, Document d2) {
        double[] v1 = getEmbedding(d1);
        double[] v2 = getEmbedding(d2);

        if (v1 == null || v2 == null) return 0.0;

        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }

        if (norm1 == 0 || norm2 == 0) return 0.0;

        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    @SuppressWarnings("unchecked")
    private double[] getEmbedding(Document doc) {
        Object emb = doc.getMetadata().get("embedding");

        if (emb instanceof double[]) return (double[]) emb;

        if (emb instanceof List<?>) {
            List<?> list = (List<?>) emb;
            double[] arr = new double[list.size()];
            for (int i = 0; i < list.size(); i++) {
                arr[i] = ((Number) list.get(i)).doubleValue();
            }
            return arr;
        }

        return null;
    }

    // ─────────────────────────────────────────────
    // Score helper
    // ─────────────────────────────────────────────
    private double score(Document doc) {
        Object finalScore = doc.getMetadata().get("final_score");
        Object rerankerScore = doc.getMetadata().get("reranker_score");
        Object retrieverScore = doc.getMetadata().get("retriever_score");

        if (finalScore instanceof Number n) return n.doubleValue();
        if (rerankerScore instanceof Number n) return n.doubleValue();
        if (retrieverScore instanceof Number n) return n.doubleValue();

        return 0.0;
    }
}
