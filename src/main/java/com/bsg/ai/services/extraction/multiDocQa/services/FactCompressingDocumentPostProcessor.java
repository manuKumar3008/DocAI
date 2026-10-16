package com.bsg.ai.services.extraction.multiDocQa.services;

import com.bsg.ai.services.extraction.multiDocQa.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FactCompressingDocumentPostProcessor implements DocumentPostProcessor {

    private final RagProperties ragProperties;
    private final EmbeddingModel embeddingModel;

    public FactCompressingDocumentPostProcessor(RagProperties ragProperties,
                                                EmbeddingModel embeddingModel) {
        this.ragProperties = ragProperties;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {

        if (documents == null || documents.isEmpty()) {
            return documents;
        }

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

        final int maxChunks = ragProperties.context().maxContextChunks();
        final int maxChars = ragProperties.context().maxCharsPerChunk();

        // 🔥 Precompute query embedding
        List<Double> queryEmbedding = embed(queryText);

        List<Document> ranked = documents.stream()
                .sorted(Comparator.comparingDouble(this::score).reversed())
                .toList();

        List<Document> result = new ArrayList<>();

        for (Document doc : ranked) {
            Document compressed = compressSemantically(doc, queryEmbedding, maxChars);
            result.add(compressed);

            if (result.size() >= maxChunks) break;
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // Semantic Compression
    // ─────────────────────────────────────────────────────────────
    private Document compressSemantically(Document doc,
                                          List<Double> queryEmbedding,
                                          int maxChars) {

        String text = doc.getText();
        if (text == null || text.isBlank() || text.length() <= maxChars) {
            return doc;
        }

        String[] sentences = text.split("(?<=[.!?])\\s+");

        // Score sentences
        List<SentenceScore> scored = new ArrayList<>();

        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i];

            List<Double> sentenceEmbedding = embed(sentence);

            double similarity = cosineSimilarity(queryEmbedding, sentenceEmbedding);

            scored.add(new SentenceScore(sentence, similarity, i));
        }

        // Sort by relevance
        List<SentenceScore> top = scored.stream()
                .sorted(Comparator.comparingDouble(SentenceScore::score).reversed())
                .limit(5) // 🔥 configurable if needed
                .toList();

        // Restore original order
        List<SentenceScore> ordered = top.stream()
                .sorted(Comparator.comparingInt(SentenceScore::index))
                .toList();

        StringBuilder builder = new StringBuilder();

        for (SentenceScore s : ordered) {
            if (builder.length() + s.text().length() + 1 > maxChars) break;

            if (!builder.isEmpty()) builder.append(" ");
            builder.append(s.text().trim());
        }

        // fallback
        if (builder.isEmpty()) {
            builder.append(text, 0, Math.min(text.length(), maxChars));
        }

        return new Document(builder.toString(), doc.getMetadata());
    }

    // ─────────────────────────────────────────────────────────────
    // Embedding helper
    // ─────────────────────────────────────────────────────────────
    private List<Double> embed(String text) {
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        float[] output = response.getResult().getOutput();
        List<Double> result = new ArrayList<>();
        for (float d : output) {
            result.add((double) d);
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // Cosine similarity
    // ─────────────────────────────────────────────────────────────
    private double cosineSimilarity(List<Double> v1, List<Double> v2) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.size(); i++) {
            dot += v1.get(i) * v2.get(i);
            normA += Math.pow(v1.get(i), 2);
            normB += Math.pow(v2.get(i), 2);
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-10);
    }

    private double score(Document doc) {
        Object finalScore = doc.getMetadata().get("final_score");
        Object rerankerScore = doc.getMetadata().get("reranker_score");
        Object retrieverScore = doc.getMetadata().get("retriever_score");

        if (finalScore instanceof Number n) return n.doubleValue();
        if (rerankerScore instanceof Number n) return n.doubleValue();
        if (retrieverScore instanceof Number n) return n.doubleValue();

        return 0.0;
    }

    // ─────────────────────────────────────────────────────────────
    // Helper method
    // ─────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────
    // Helper record
    // ─────────────────────────────────────────────────────────────
    private record SentenceScore(String text, double score, int index) {}
}
