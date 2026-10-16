package com.bsg.ai.services.extraction.classification.service;

import com.bsg.ai.services.config.ApplicationPrompt;
import com.bsg.ai.services.extraction.classification.pojo.OCROutput;
import com.bsg.ai.services.extraction.classification.pojo.SummaryResult;
import com.bsg.ai.services.extraction.filter.dto.SubjectsResponse;
import com.bsg.ai.services.extraction.filter.service.ExternalAPIService;
import com.bsg.ai.services.extraction.services.service.DocumentProcessingService;
import com.bsg.ai.services.extraction.services.service.ElasticSearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
@Deprecated
public class ClassificationServiceOld {
    public static final int NUMBER_OF_PAGES_FOR_CLASSIFICATION = 3;
    private static final int SUMMARY_TARGET_TOKENS = 300;
    private static final int LLM_CONTEXT_WINDOW_SIZE = 7000;
    private static final int MIN_SUMMARY_TARGET_TOKENS = 64;
    private static final int MAX_NO_PROGRESS_ATTEMPTS = 3;

    private final ApplicationPrompt prompt;
    private final ObjectMapper mapper;
    private final ChatClient chatClient;
    private final ExternalAPIService externalAPIService;
    private final DocumentProcessingService documentProcessingService;
    private final ElasticSearchService elasticSearchService;
    private final SummarizationPipeline summarizationPipeline;
    private final int chunkFetchMaxRetries;
    private final long chunkFetchDelayMs;
    private final Duration promptCacheTtl;
    private final ExecutorService summarizationExecutor;

    private volatile CachedPrompt cachedPrompt;

    public ClassificationServiceOld(ApplicationPrompt prompt,
                                 ObjectMapper mapper,
                                 ChatClient.Builder builder,
                                 ExternalAPIService externalAPIService,
                                 DocumentProcessingService documentProcessingService,
                                 ElasticSearchService elasticSearchService,
                                 SummarizationPipeline summarizationPipeline,
                                 @Value("${classification.chunk-fetch.max-retries:3}") int chunkFetchMaxRetries,
                                 @Value("${classification.chunk-fetch.delay-ms:1000}") long chunkFetchDelayMs,
                                 @Value("${classification.summarization.parallelism:4}") int summarizationParallelism,
                                 @Value("${classification.prompt.cache.ttl-minutes:10}") long promptCacheTtlMinutes) {
        this.prompt = prompt;
        this.mapper = mapper;
        this.chatClient = builder.build();
        this.externalAPIService = externalAPIService;
        this.documentProcessingService = documentProcessingService;
        this.elasticSearchService = elasticSearchService;
        this.summarizationPipeline = summarizationPipeline;
        this.chunkFetchMaxRetries = Math.max(1, chunkFetchMaxRetries);
        this.chunkFetchDelayMs = Math.max(0L, chunkFetchDelayMs);
        this.promptCacheTtl = Duration.ofMinutes(Math.max(1L, promptCacheTtlMinutes));
        this.summarizationExecutor = Executors.newFixedThreadPool(Math.max(1, summarizationParallelism));
    }

    @PreDestroy
    void shutdownExecutor() {
        summarizationExecutor.shutdown();
    }

    public JsonNode processResource(MultipartFile file, String documentId) {
        long startMs = System.currentTimeMillis();
        try {
            documentProcessingService.ingestDocumentWithSemanticStitching(file, documentId, false);
            List<Document> allChunks = waitForChunks(documentId);
            if (allChunks.isEmpty()) {
                return mapper.createObjectNode().put("message", "No chunks found");
            }

            JsonNode result = classifyViaChunkSummaries(allChunks, prepareDocClassificationPrompt());
            log.info("Classification completed for documentId={} in {} ms", documentId, System.currentTimeMillis() - startMs);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to process classification request for documentId=" + documentId, e);
        }
    }

    private List<Document> waitForChunks(String documentId) throws IOException {
        for (int attempt = 1; attempt <= chunkFetchMaxRetries; attempt++) {
            List<Document> chunks = elasticSearchService.getTopAndBottomNPageChunks(
                    documentId, NUMBER_OF_PAGES_FOR_CLASSIFICATION);
            if (chunks != null && !chunks.isEmpty()) {
                return chunks;
            }

            if (attempt < chunkFetchMaxRetries && chunkFetchDelayMs > 0) {
                try {
                    Thread.sleep(chunkFetchDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for chunks", e);
                }
            }
        }
        return Collections.emptyList();
    }

    private String prepareDocClassificationPrompt() throws JsonProcessingException {
        CachedPrompt localCache = cachedPrompt;
        if (localCache != null && localCache.isValid(promptCacheTtl)) {
            return localCache.value();
        }

        synchronized (this) {
            localCache = cachedPrompt;
            if (localCache != null && localCache.isValid(promptCacheTtl)) {
                return localCache.value();
            }

            var subjectResponse = externalAPIService.getSubjectsResponse();
            String basePrompt = this.prompt.getDocClassificationPrompt();
            List<CategoryRecord> categoryRecords = new ArrayList<>();
            for (SubjectsResponse.DocumentTypeWiseSubjects dt : subjectResponse.getDocumentTypeWiseSubjects()) {
                String category = dt.getDocumentType();
                List<String> subCategories = dt.getSubjects() == null
                        ? List.of()
                        : dt.getSubjects().stream().map(SubjectsResponse.SubjectKeyValue::getKey).toList();
                categoryRecords.add(new CategoryRecord(category, subCategories));
            }

            String categoriesMap = mapper.writeValueAsString(categoryRecords);
            String formattedPrompt = basePrompt.replace("{DESCRIPTION_SECTION}", categoriesMap);
            cachedPrompt = new CachedPrompt(formattedPrompt, Instant.now());
            return formattedPrompt;
        }
    }

    private JsonNode classifyViaChunkSummaries(List<Document> allChunks, String systemPrompt) throws JsonProcessingException {
        int maxClassificationInputChars = Math.max(1000, LLM_CONTEXT_WINDOW_SIZE - systemPrompt.length());
        String combinedSummary = collapseSummariesToFit(allChunks, maxClassificationInputChars);
        String userPrompt = """
        Classify the following document using summary.
        summary:
        %s
        """.formatted(combinedSummary);

Prompt prompt = Prompt.builder()
        .messages(
                SystemMessage.builder()
                        .text(systemPrompt)
                        .build(),
                UserMessage.builder()
                        .text(userPrompt)
                        .build()
        )
        .build();

        var response = this.chatClient.prompt(prompt).call().content();
        log.debug("Chunk-summary classification raw response: {}", response);
        return convertToJSONNode(response);
    }

    private String collapseSummariesToFit(List<Document> allChunks, int maxClassificationInputChars) {
        List<String> currentPayloads = allChunks.stream()
                .map(Document::getText)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(ArrayList::new));
        if (currentPayloads.isEmpty()) {
            return "";
        }

        int iteration = 0;
        while (currentPayloads.size() > 1) {
            iteration++;
            int currentCount = currentPayloads.size();
            List<List<String>> groupedPayloads = buildDynamicGroups(currentPayloads, maxClassificationInputChars);
            int targetTokens = computeDynamicTargetTokens(maxClassificationInputChars, currentPayloads, groupedPayloads);
            List<SummaryResult> reducedSummaries = summarizeGroups(groupedPayloads, targetTokens);
            List<String> nextPayloads = prepareNextLevelPayloads(reducedSummaries);

            if (nextPayloads.isEmpty()) {
                throw new IllegalStateException(
                        "Hierarchical summarization returned empty output for current=" + currentCount);
            }
            if (nextPayloads.size() >= currentCount && nextPayloads.size() > 1) {
                nextPayloads = forceMergePayloads(nextPayloads);
            }
            if (nextPayloads.size() >= currentCount && nextPayloads.size() > 1) {
                throw new IllegalStateException(
                        "Hierarchical summarization did not reduce summary count: current="
                                + currentCount + ", next=" + nextPayloads.size());
            }

            currentPayloads = nextPayloads;
            log.debug("Summary collapse iteration={} from={} to={} groups={} tokens={}",
                    iteration, currentCount, currentPayloads.size(), groupedPayloads.size(), targetTokens);
        }

        return summarizeUntilFits(currentPayloads.get(0), maxClassificationInputChars);
    }

    private List<List<String>> buildDynamicGroups(List<String> payloads, int maxClassificationInputChars) {
        int totalChars = payloads.stream().mapToInt(String::length).sum();
        int budget = Math.max(maxClassificationInputChars, Math.max(1, totalChars / Math.max(1, payloads.size())));
        List<List<String>> groups = groupPayloadsByBudget(payloads, budget);

        while (groups.size() >= payloads.size() && payloads.size() > 1 && budget < totalChars) {
            budget = Math.min(totalChars, budget + Math.max(1, budget / 2));
            groups = groupPayloadsByBudget(payloads, budget);
        }

        if (groups.size() >= payloads.size() && payloads.size() > 1) {
            groups = forcePairGroups(payloads);
        }
        return groups;
    }

    private List<List<String>> groupPayloadsByBudget(List<String> payloads, int budget) {
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentSize = 0;

        for (String payload : payloads) {
            int payloadSize = payload.length();
            if (!current.isEmpty() && currentSize + payloadSize > budget) {
                groups.add(current);
                current = new ArrayList<>();
                currentSize = 0;
            }
            current.add(payload);
            currentSize += payloadSize;
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        return groups;
    }

    private List<List<String>> forcePairGroups(List<String> payloads) {
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>(2);
        for (String payload : payloads) {
            current.add(payload);
            if (current.size() == 2) {
                groups.add(current);
                current = new ArrayList<>(2);
            }
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        return groups;
    }

    private int computeDynamicTargetTokens(int maxClassificationInputChars, List<String> currentPayloads,
                                           List<List<String>> groupedPayloads) {
        if (groupedPayloads.isEmpty()) {
            return MIN_SUMMARY_TARGET_TOKENS;
        }
        int totalChars = currentPayloads.stream().mapToInt(String::length).sum();
        int avgInputPerGroup = Math.max(1, totalChars / groupedPayloads.size());
        double compressionPressure = (double) maxClassificationInputChars / avgInputPerGroup;
        double factor = Math.max(0.25d, Math.min(1.0d, compressionPressure));
        int dynamicTokens = (int) Math.round(SUMMARY_TARGET_TOKENS * factor);
        return Math.max(MIN_SUMMARY_TARGET_TOKENS, dynamicTokens);
    }

    private List<String> forceMergePayloads(List<String> payloads) {
        List<List<String>> forcedGroups = forcePairGroups(payloads);
        return forcedGroups.stream()
                .map(this::toJsonArrayPayload)
                .toList();
    }

    private List<SummaryResult> summarizeGroups(List<List<String>> groupedPayloads, int targetTokens) {
        List<CompletableFuture<IndexedSummary>> futures = IntStream.range(0, groupedPayloads.size())
                .mapToObj(index -> CompletableFuture.supplyAsync(() -> {
                    String payload = toJsonArrayPayload(groupedPayloads.get(index));
                    SummaryResult result = summarizationPipeline.summarize(index + 1, targetTokens, payload);
                    return new IndexedSummary(index, result);
                }, summarizationExecutor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .sorted(Comparator.comparingInt(IndexedSummary::index))
                .map(IndexedSummary::summary)
                .toList();
    }

    private String toJsonArrayPayload(List<String> group) {
        ArrayNode arrayNode = mapper.createArrayNode();
        for (String item : group) {
            JsonNode parsed = parseJsonNode(item);
            if (parsed != null) {
                arrayNode.add(parsed);
            } else {
                arrayNode.add(item);
            }
        }
        return arrayNode.toString();
    }

    private JsonNode parseJsonNode(String text) {
        try {
            return mapper.readTree(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> prepareNextLevelPayloads(List<SummaryResult> summaries) {
        Set<String> seenDates = new LinkedHashSet<>();
        Set<String> seenNumbers = new LinkedHashSet<>();
        Set<String> seenFacts = new LinkedHashSet<>();
        Set<String> seenEntities = new LinkedHashSet<>();

        List<String> payloads = new ArrayList<>();
        for (SummaryResult summary : summaries) {
            String payload = summaryToPayload(summary, seenDates, seenNumbers, seenFacts, seenEntities);
            if (StringUtils.isNotBlank(payload)) {
                payloads.add(payload);
            }
        }
        return payloads;
    }

    private String summaryToPayload(SummaryResult summary,
                                    Set<String> seenDates,
                                    Set<String> seenNumbers,
                                    Set<String> seenFacts,
                                    Set<String> seenEntities) {
        if (summary == null) {
            return "";
        }

        ObjectNode payload = mapper.createObjectNode();
        if (StringUtils.isNotBlank(summary.getCompressedSummary())) {
            payload.put("compressed_summary", summary.getCompressedSummary().trim());
        }

        ArrayNode uniqueDates = uniqueStringArray(summary.getDates(), seenDates);
        if (!uniqueDates.isEmpty()) {
            payload.set("dates", uniqueDates);
        }

        ArrayNode uniqueNumbers = uniqueStringArray(summary.getNumbers(), seenNumbers);
        if (!uniqueNumbers.isEmpty()) {
            payload.set("numbers", uniqueNumbers);
        }

        ArrayNode uniqueFacts = uniqueStringArray(summary.getKeyFacts(), seenFacts);
        if (!uniqueFacts.isEmpty()) {
            payload.set("key_facts", uniqueFacts);
        }

        ArrayNode uniqueEntities = mapper.createArrayNode();
        if (summary.getKeyEntities() != null) {
            summary.getKeyEntities().forEach(entity -> {
                if (entity == null) {
                    return;
                }
                String entityType = StringUtils.defaultString(entity.getType()).trim();
                String entityValue = StringUtils.defaultString(entity.getValue()).trim();
                if (entityValue.isEmpty()) {
                    return;
                }
                String dedupeKey = entityType + "|" + entityValue.toLowerCase();
                if (seenEntities.add(dedupeKey)) {
                    ObjectNode item = mapper.createObjectNode();
                    item.put("type", entityType);
                    item.put("value", entityValue);
                    uniqueEntities.add(item);
                }
            });
        }
        if (!uniqueEntities.isEmpty()) {
            payload.set("key_entities", uniqueEntities);
        }

        if (payload.isEmpty()) {
            return "";
        }
        return payload.toString();
    }

    private ArrayNode uniqueStringArray(List<String> values, Set<String> seen) {
        ArrayNode array = mapper.createArrayNode();
        if (values == null) {
            return array;
        }
        for (String value : values) {
            String normalized = StringUtils.defaultString(value).trim();
            if (normalized.isEmpty()) {
                continue;
            }
            String dedupeKey = normalized.toLowerCase();
            if (seen.add(dedupeKey)) {
                array.add(normalized);
            }
        }
        return array;
    }

    private String summarizeUntilFits(String combined, int maxClassificationInputChars) {
        String current = combined;
        int targetTokens = SUMMARY_TARGET_TOKENS;
        int noProgressAttempts = 0;
        int attempts = 0;

        while (current.length() > maxClassificationInputChars) {
            attempts++;
            SummaryResult summaryResult = summarizationPipeline.summarize(1, targetTokens, current);
            String reduced = summaryToPayload(summaryResult,
                    new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
            if (StringUtils.isBlank(reduced)) {
                throw new IllegalStateException("Re-summarization returned empty content while reducing classification input");
            }

            if (reduced.length() >= current.length()) {
                noProgressAttempts++;
                targetTokens = Math.max(MIN_SUMMARY_TARGET_TOKENS, targetTokens / 2);
                if (noProgressAttempts >= MAX_NO_PROGRESS_ATTEMPTS) {
                    throw new IllegalStateException(
                            "Unable to reduce summary below classification input size after repeated attempts");
                }
            } else {
                current = reduced;
                noProgressAttempts = 0;
                if (current.length() > maxClassificationInputChars) {
                    targetTokens = Math.max(MIN_SUMMARY_TARGET_TOKENS, targetTokens / 2);
                }
            }
            log.debug("Final re-summarization attempt={} targetTokens={} length={}",
                    attempts, targetTokens, current.length());
        }

        return current;
    }

    private JsonNode convertToJSONNode(String text) {
        try {
            String cleaned = Optional.ofNullable(text).orElse("");
            if (cleaned.contains("```json")) {
                cleaned = cleaned.replace("```json", "");
            }
            if (cleaned.contains("```")) {
                cleaned = cleaned.replace("```", "");
            }
            return mapper.readTree(cleaned);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse generated JSON: {}", text, e);
            throw new RuntimeException(e);
        }
    }

    public OCROutput convertToClassificationResponseDto(JsonNode jsonNode) {
        var classificationResponseDto = new OCROutput();
        String category = readText(jsonNode, "klassifikation", "hauptkategorie", "value");
        double categoryConfidence = readDouble(jsonNode, "klassifikation", "hauptkategorie", "confidence");
        String subCategory = readText(jsonNode, "klassifikation", "unterkategorie", "value");
        double subCategoryConfidence = readDouble(jsonNode, "klassifikation", "unterkategorie", "confidence");
        String documentDate = readText(jsonNode, "klassifikation", "dokument_datum", "value");
        double documentDateConfidence = readDouble(jsonNode, "klassifikation", "dokument_datum", "confidence");
        String entity = readText(jsonNode, "klassifikation", "entitaet", "value");
        double entityConfidence = readDouble(jsonNode, "klassifikation", "entitaet", "confidence");

        classificationResponseDto.setClassifications(List.of(Map.of("label", category, "score", categoryConfidence)));
        classificationResponseDto.setProperties(Map.of(
                "Unterkategorie", List.of(Map.of("value", subCategory, "score", subCategoryConfidence)),
                "Datum", List.of(Map.of("value", documentDate, "score", documentDateConfidence)),
                "Name", List.of(Map.of("value", entity, "score", entityConfidence))
        ));
        classificationResponseDto.setBarcodes(List.of());
        return classificationResponseDto;
    }

    private String readText(JsonNode jsonNode, String... path) {
        JsonNode node = jsonNode;
        for (String segment : path) {
            if (node == null) {
                return "";
            }
            node = node.get(segment);
        }
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private double readDouble(JsonNode jsonNode, String... path) {
        JsonNode node = jsonNode;
        for (String segment : path) {
            if (node == null) {
                return 0.0d;
            }
            node = node.get(segment);
        }
        return node == null || node.isNull() ? 0.0d : node.asDouble(0.0d);
    }

    private record CategoryRecord(String category, List<String> subCategories) {
    }

    private record CachedPrompt(String value, Instant createdAt) {
        private boolean isValid(Duration ttl) {
            return createdAt.plus(ttl).isAfter(Instant.now());
        }
    }

    private record IndexedSummary(int index, SummaryResult summary) {
    }
}
