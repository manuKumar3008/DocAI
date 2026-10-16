package com.bsg.ai.services.extraction.classification.service;

import com.bsg.ai.services.config.ApplicationPrompt;
import com.bsg.ai.services.extraction.classification.pojo.DocumentTypeCandidate;
import com.bsg.ai.services.extraction.classification.pojo.ExtractedField;
import com.bsg.ai.services.extraction.classification.pojo.OCROutput;
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

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClassificationService {

    private static final String KEYWORD_QUERY = "invoice contract resume report prescription form";
    private static final int LLM_CONTEXT_WINDOW_CHARS = 7000;
    private static final int RESERVED_OUTPUT_AND_OVERHEAD_CHARS = 1200;
    private static final int AVAILABLE_INPUT_CHARS = LLM_CONTEXT_WINDOW_CHARS - RESERVED_OUTPUT_AND_OVERHEAD_CHARS;
    private static final int SUMMARIZATION_SYSTEM_PROMPT_CHARS = 400;
    private static final int SUMMARIZATION_USER_PREFIX_CHARS = 80;
    private static final int SUMMARIZER_CHUNK_BUDGET_CHARS =
            AVAILABLE_INPUT_CHARS - SUMMARIZATION_SYSTEM_PROMPT_CHARS - SUMMARIZATION_USER_PREFIX_CHARS;
    private static final int CLASSIFIER_SUMMARY_MAX_CHARS = 4200;

    private final ExecutorService summarizerExecutor;
    private final ApplicationPrompt appPrompt;
    private final ObjectMapper mapper;
    private final ChatClient chatClient;
    private final ExternalAPIService externalAPIService;
    private final DocumentProcessingService documentProcessingService;
    private final ElasticSearchService elasticSearchService;
    private final int chunkFetchMaxRetries;
    private final long chunkFetchDelayMs;
    private final Duration promptCacheTtl;

    private volatile CachedPrompt cachedPrompt;

    public ClassificationService(
            ApplicationPrompt appPrompt,
            ObjectMapper mapper,
            ChatClient.Builder builder,
            ExternalAPIService externalAPIService,
            DocumentProcessingService documentProcessingService,
            ElasticSearchService elasticSearchService,
            @Value("${classification.chunk-fetch.max-retries:3}") int chunkFetchMaxRetries,
            @Value("${classification.chunk-fetch.delay-ms:1000}") long chunkFetchDelayMs,
            @Value("${classification.prompt.cache.ttl-minutes:10}") long promptCacheTtlMinutes,
            @Value("${classification.summarizer.parallelism:4}") int summarizerParallelism
    ) {
        this.appPrompt = appPrompt;
        this.mapper = mapper;
        this.chatClient = builder.build();
        this.externalAPIService = externalAPIService;
        this.documentProcessingService = documentProcessingService;
        this.elasticSearchService = elasticSearchService;
        this.chunkFetchMaxRetries = Math.max(1, chunkFetchMaxRetries);
        this.chunkFetchDelayMs = Math.max(0L, chunkFetchDelayMs);
        this.promptCacheTtl = Duration.ofMinutes(Math.max(1L, promptCacheTtlMinutes));
        this.summarizerExecutor = Executors.newFixedThreadPool(Math.max(1, summarizerParallelism));
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public JsonNode processResource(MultipartFile file, String documentId) {
    return processResource(file, documentId, getDefaultTaxonomy());
}

    public JsonNode processResource(MultipartFile file, String documentId, List<DocumentTypeCandidate> taxonomy) {
        long startMs = System.currentTimeMillis();
        try {
            List<DocumentTypeCandidate> safeTaxonomy = taxonomy == null ? List.of() : taxonomy;

            documentProcessingService.ingestDocumentWithSemanticStitching(file, documentId, false);

            Optional<ElasticSearchService.PageBounds> boundsOpt = waitForBounds(documentId);
            if (boundsOpt.isEmpty()) {
                return mapper.createObjectNode().put("message", "No chunks found");
            }

            ElasticSearchService.PageBounds bounds = boundsOpt.get();
            List<Document> selected = selectRepresentativeChunks(documentId, bounds, 5, true);

            String summary = summarizeChunks(selected);
            log.debug("LLM summary for documentId={}: {}", documentId, summary);

            JsonNode result = classifyWithRetry(documentId, bounds, summary, safeTaxonomy);

            double confidence = readDouble(result, "klassifikation", "konfidenz_gesamt");
            if (confidence < 0.6d && result instanceof ObjectNode rootObj) {
                writeNestedValue(rootObj, 0.59d, "klassifikation", "konfidenz_gesamt");
            }

            if (!safeTaxonomy.isEmpty()) {
                result = resolveAgainstAdminTaxonomy(result, safeTaxonomy);

                DocumentTypeCandidate matchedType = findMatchedAdminCandidate(result, safeTaxonomy);
                if (matchedType != null
                        && matchedType.getMandatoryFields() != null
                        && !matchedType.getMandatoryFields().isEmpty()
                        && result instanceof ObjectNode rootObj) {
                    JsonNode extractedFields = extractRequiredFields(summary, matchedType);
                    mergeRequiredFields(rootObj, extractedFields);
                }

                log.info("Admin-taxonomy classification completed for documentId={} in {} ms",
                        documentId, System.currentTimeMillis() - startMs);
            } else {
                log.info("External-taxonomy classification completed for documentId={} in {} ms",
                        documentId, System.currentTimeMillis() - startMs);
            }

            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to process classification request for documentId " + documentId, e);
        }
    }

    private JsonNode classifyWithRetry(
            String documentId,
            ElasticSearchService.PageBounds bounds,
            String summary,
            List<DocumentTypeCandidate> taxonomy
    ) throws IOException {
        JsonNode result = classify(summary, taxonomy);
        double confidence = readDouble(result, "klassifikation", "konfidenz_gesamt");

        if (confidence < 0.7d) {
            List<Document> retrySelected = selectRepresentativeChunks(documentId, bounds, 10, true);
            String retrySummary = summarizeChunks(retrySelected);

            JsonNode retryResult = classify(retrySummary, taxonomy);
            double retryConfidence = readDouble(retryResult, "klassifikation", "konfidenz_gesamt");

            if (retryConfidence > confidence) {
                return retryResult;
            }
        }

        return result;
    }

    private JsonNode classify(String summary, List<DocumentTypeCandidate> taxonomy) throws JsonProcessingException {
        if (taxonomy == null || taxonomy.isEmpty()) {
            String prompt = prepareDocClassificationPrompt();
            return classifyFromSummary(summary, prompt, true);
        }

        String prompt = buildAdminTaxonomyClassificationPrompt(taxonomy);
        return classifyFromSummary(summary, prompt, false);
    }

    private List<DocumentTypeCandidate> getDefaultTaxonomy() {
    SubjectsResponse subjectsResponse = externalAPIService.getSubjectsResponse();
    if (subjectsResponse == null || subjectsResponse.getDocumentTypeWiseSubjects() == null) {
        return List.of();
    }

    return subjectsResponse.getDocumentTypeWiseSubjects().stream()
            .filter(Objects::nonNull)
            .map(dt -> new DocumentTypeCandidate(
                    null,
                    dt.getDocumentType(),
                    dt.getDocumentType(),
                    List.of()
            ))
            .toList();
}

    // -------------------------------------------------------------------------
    // Admin taxonomy prompt
    // -------------------------------------------------------------------------

    private String buildAdminTaxonomyClassificationPrompt(List<DocumentTypeCandidate> taxonomy) throws JsonProcessingException {
        String categoriesJson = mapper.writeValueAsString(taxonomy);

        return appPrompt.getDocClassificationPrompt()
                .replace("{DESCRIPTION_SECTION}",
                        categoriesJson + System.lineSeparator()
                                + "Only classify against the document types listed above. "
                                + "Use the exact name value from the list for hauptkategorie. "
                                + "If the document does not clearly match any listed type, "
                                + "set hauptkategorie.value to UNKNOWN and confidence to 0.")
                + System.lineSeparator()
                + appPrompt.getDocClassificationPromptResponseFormat();
    }

    private JsonNode resolveAgainstAdminTaxonomy(JsonNode result, List<DocumentTypeCandidate> taxonomy) {
        if (!(result instanceof ObjectNode rootObj)) {
            return result;
        }

        String predictedName = readText(result, "klassifikation", "hauptkategorie", "value");
        DocumentTypeCandidate match = null;
        int bestScore = -1;

        for (DocumentTypeCandidate c : taxonomy) {
            if (c == null || StringUtils.isBlank(c.getName())) {
                continue;
            }
            int score = matchScore(predictedName, c.getName());
            if (score > bestScore) {
                bestScore = score;
                match = c;
            }
        }

        if (bestScore <= 0 || match == null) {
            writeNestedValue(rootObj, "UNKNOWN", "klassifikation", "hauptkategorie", "value");
            writeNestedValue(rootObj, 0.0d, "klassifikation", "konfidenz_gesamt");
            writeNestedValue(rootObj, (String) null, "klassifikation", "hauptkategorie", "id");
            writeStringList(rootObj, List.of(), "klassifikation", "requiredFields");
        } else {
            writeNestedValue(rootObj, match.getName(), "klassifikation", "hauptkategorie", "value");
            writeNestedValue(rootObj, match.getId(), "klassifikation", "hauptkategorie", "id");
            writeStringList(rootObj, match.getMandatoryFields(), "klassifikation", "requiredFields");
        }

        return rootObj;
    }

    private DocumentTypeCandidate findMatchedAdminCandidate(JsonNode result, List<DocumentTypeCandidate> taxonomy) {
        String categoryId = readText(result, "klassifikation", "hauptkategorie", "id");
        String categoryName = readText(result, "klassifikation", "hauptkategorie", "value");

        for (DocumentTypeCandidate candidate : taxonomy) {
            if (candidate == null) {
                continue;
            }

            if (StringUtils.isNotBlank(categoryId) && StringUtils.equals(candidate.getId(), categoryId)) {
                return candidate;
            }

            if (StringUtils.isNotBlank(categoryName) && StringUtils.equalsIgnoreCase(candidate.getName(), categoryName)) {
                return candidate;
            }
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Second extraction step for mandatory fields
    // -------------------------------------------------------------------------

    private JsonNode extractRequiredFields(String summary, DocumentTypeCandidate matchedType) throws JsonProcessingException {
        if (StringUtils.isBlank(summary)
                || matchedType == null
                || matchedType.getMandatoryFields() == null
                || matchedType.getMandatoryFields().isEmpty()) {
            return mapper.createObjectNode();
        }

        String systemPrompt = """
                You are a document information extraction assistant.
                Extract only the requested required fields from the provided document summary.
                If a field is missing, return an empty string for value and 0.0 for confidence.
                Return ONLY a valid JSON object.
                Do not include markdown, explanations, or extra keys.
                """;

        String fieldsJson = mapper.writeValueAsString(matchedType.getMandatoryFields());

        String userPrompt = """
                Document type: %s
                Description: %s
                Required fields: %s

                Extract the required fields from the document summary below.

                Return exactly this JSON structure:
                {
                  "requiredFieldMapping": {
                    "<field_name>": {
                      "value": "",
                      "confidence": 0.0
                    }
                  }
                }

                Document summary:
                %s
                """.formatted(
                StringUtils.defaultString(matchedType.getName()),
                StringUtils.defaultString(matchedType.getDescription()),
                fieldsJson,
                summary
        );

        String raw = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

        log.debug("Required-field extraction raw response for docType={}: {}", matchedType.getName(), raw);
        return convertToJSONNode(raw);
    }

    private void mergeRequiredFields(ObjectNode classificationResult, JsonNode extractedFields) {
        if (classificationResult == null || extractedFields == null || extractedFields.isNull()) {
            return;
        }

        JsonNode requiredFieldMapping = extractedFields.get("requiredFieldMapping");
        if (requiredFieldMapping != null && requiredFieldMapping.isObject()) {
            classificationResult.set("requiredFieldMapping", requiredFieldMapping);
        }
    }

    // -------------------------------------------------------------------------
    // Response DTO conversion
    // -------------------------------------------------------------------------

    public OCROutput convertToClassificationResponseDto(JsonNode jsonNode) {
        String category = readText(jsonNode, "klassifikation", "hauptkategorie", "value");
        String categoryId = readText(jsonNode, "klassifikation", "hauptkategorie", "id");
        double categoryConfidence = readDouble(jsonNode, "klassifikation", "hauptkategorie", "confidence");

        String subCategory = readText(jsonNode, "klassifikation", "unterkategorie", "value");
        double subCategoryConfidence = readDouble(jsonNode, "klassifikation", "unterkategorie", "confidence");

        String documentDate = readText(jsonNode, "klassifikation", "dokument_datum", "value");
        double documentDateConf = readDouble(jsonNode, "klassifikation", "dokument_datum", "confidence");

        String entity = readText(jsonNode, "klassifikation", "entitaet", "value");
        double entityConfidence = readDouble(jsonNode, "klassifikation", "entitaet", "confidence");

        OCROutput dto = new OCROutput();

        dto.setClassifications(List.of(Map.of(
                "label", category,
                "score", categoryConfidence,
                "id", categoryId == null ? "" : categoryId
        )));

        dto.setProperties(Map.of(
                "Unterkategorie", List.of(Map.of("value", subCategory, "score", subCategoryConfidence)),
                "Datum", List.of(Map.of("value", documentDate, "score", documentDateConf)),
                "Name", List.of(Map.of("value", entity, "score", entityConfidence))
        ));

        dto.setRequiredFieldMapping(readRequiredFieldMapping(jsonNode));
        dto.setBarcodes(List.of());
        return dto;
    }

    private Map<String, ExtractedField> readRequiredFieldMapping(JsonNode root) {
        JsonNode node = root.get("requiredFieldMapping");
        if (node == null || !node.isObject()) {
            return Map.of();
        }

        Map<String, ExtractedField> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode valueNode = entry.getValue();
            String value = valueNode.path("value").asText("");
            double confidence = valueNode.path("confidence").asDouble(0.0);
            result.put(entry.getKey(), new ExtractedField(value, confidence));
        });

        return result;
    }

    // -------------------------------------------------------------------------
    // LLM summarisation
    // -------------------------------------------------------------------------

   private String summarizeChunks(List<Document> chunks) {
    if (chunks == null || chunks.isEmpty()) {
        log.warn("summarizeChunks: no chunks provided");
        return "";
    }

    List<Document> usableChunks = chunks.stream()
            .filter(Objects::nonNull)
            .filter(d -> StringUtils.isNotBlank(d.getText()))
            .toList();

    if (usableChunks.isEmpty()) {
        log.warn("summarizeChunks: all selected chunks have blank text");
        return "";
    }

    String sysPrompt = StringUtils.defaultIfBlank(
            appPrompt.getDocSummaryPrompt(),
            "You are a document analyst. Summarize the given document chunks in max 80 words. "
                    + "You MUST preserve document type, dates, names, amounts, IDs, reference numbers, "
                    + "and subject keywords. Output plain text only."
    );

    List<String> batches = splitIntoBatches(usableChunks, SUMMARIZER_CHUNK_BUDGET_CHARS);

    if (batches.isEmpty()) {
        log.warn("summarizeChunks: no text content in selected chunks after filtering");
        return "";
    }

    log.info("summarizeChunks: {} chars split into {} batch(es), firing in parallel",
            batches.stream().mapToInt(String::length).sum(), batches.size());

    List<CompletableFuture<String>> futures = batches.stream()
            .map(batch -> CompletableFuture.supplyAsync(() -> callSummarizer(sysPrompt, batch), summarizerExecutor))
            .toList();

    List<String> partials = futures.stream()
            .map(CompletableFuture::join)
            .filter(StringUtils::isNotBlank)
            .toList();

    if (partials.isEmpty()) {
        log.warn("summarizeChunks: all batch summarisation calls returned blank");
        return "";
    }

    String merged = partials.size() == 1 ? partials.get(0) : mergePartialSummaries(sysPrompt, partials);

    if (merged.length() > CLASSIFIER_SUMMARY_MAX_CHARS) {
        merged = merged.substring(0, CLASSIFIER_SUMMARY_MAX_CHARS);
    }

    log.debug("summarizeChunks: final summary ({} chars): {}", merged.length(), merged);
    return merged;
}

    private List<String> splitIntoBatches(List<Document> chunks, int batchSizeChars) {
        List<String> batches = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (Document doc : chunks) {
            int start = pageInt(doc, "startPage", -1);
            int end = pageInt(doc, "endPage", -1);
            String text = StringUtils.defaultString(doc.getText()).trim();

            if (text.isEmpty()) {
                continue;
            }

            String block = "pages " + start + "-" + end + System.lineSeparator() + text + System.lineSeparator();

            if (current.length() > 0 && current.length() + block.length() > batchSizeChars) {
                batches.add(current.toString());
                current = new StringBuilder();
            }

            if (block.length() > batchSizeChars) {
                log.warn("splitIntoBatches: chunk pages {}-{} is {} chars, larger than batch budget {}. It will be trimmed.",
                        start, end, block.length(), batchSizeChars);
                batches.add(block.substring(0, batchSizeChars));
            } else {
                current.append(block);
            }
        }

        if (!current.isEmpty()) {
            batches.add(current.toString());
        }

        return batches;
    }

    private String callSummarizer(String systemPrompt, String batchText) {
        try {
            Prompt prompt = Prompt.builder()
                    .messages(
                            SystemMessage.builder().text(systemPrompt).build(),
                            UserMessage.builder()
                                    .text("Summarize the following document chunks:\n" + batchText)
                                    .build()
                    )
                    .build();

            String result = StringUtils.defaultString(chatClient.prompt(prompt).call().content()).trim();
            log.debug("callSummarizer: batch ({} chars) -> partial summary ({} chars)",
                    batchText.length(), result.length());
            return result;
        } catch (Exception e) {
            log.error("callSummarizer: LLM call failed for batch of {} chars", batchText.length(), e);
            return "";
        }
    }

    private static final int MAX_MERGE_DEPTH = 5;

private String mergePartialSummaries(String systemPrompt, List<String> partials) {
    return mergePartialSummaries(systemPrompt, partials, 0);
}

private String mergePartialSummaries(String systemPrompt, List<String> partials, int depth) {
    String joined = partials.stream()
            .map(s -> "- " + s)
            .collect(Collectors.joining(System.lineSeparator()));

    if (joined.length() <= SUMMARIZER_CHUNK_BUDGET_CHARS) {
        log.info("mergePartialSummaries: reducing {} partial(s) ({} chars) in one call at depth {}",
                partials.size(), joined.length(), depth);
        return callSummarizer(systemPrompt,
                "The following are partial summaries of sections of the same document. Merge them into one concise summary.\n"
                        + joined);
    }

    // Safety valve: stop recursing if we've hit the depth cap, or if we're not
    // making real progress (grouping isn't reducing the item count).
    if (depth >= MAX_MERGE_DEPTH) {
        log.warn("mergePartialSummaries: hit max depth {} with {} partial(s) still unmerged ({} chars). "
                        + "Forcing truncated single-pass merge instead of recursing further.",
                MAX_MERGE_DEPTH, partials.size(), joined.length());
        String truncated = joined.length() > SUMMARIZER_CHUNK_BUDGET_CHARS
                ? joined.substring(0, SUMMARIZER_CHUNK_BUDGET_CHARS)
                : joined;
        return callSummarizer(systemPrompt,
                "The following are partial summaries of sections of the same document. Merge them into one concise summary.\n"
                        + truncated);
    }

    log.info("mergePartialSummaries: {} partial(s) too large for one reduce call, grouping recursively (depth {})",
            partials.size(), depth);

    List<String> groups = new ArrayList<>();
    StringBuilder group = new StringBuilder();

    for (String p : partials) {
        String line = "- " + p + System.lineSeparator();
        if (group.length() > 0 && group.length() + line.length() > SUMMARIZER_CHUNK_BUDGET_CHARS) {
            groups.add(callSummarizer(systemPrompt,
                    "Merge these partial document summaries into one group:\n" + group));
            group = new StringBuilder();
        }
        group.append(line);
    }

    if (!group.isEmpty()) {
        groups.add(callSummarizer(systemPrompt,
                "Merge these partial document summaries into one group:\n" + group));
    }

    // Safety valve: if grouping didn't actually shrink the list, further
    // recursion will just repeat the same non-convergent pattern. Force
    // termination instead of recursing on a same-or-larger list.
    if (groups.size() >= partials.size()) {
        log.warn("mergePartialSummaries: grouping made no progress ({} partial(s) -> {} group(s)) at depth {}. "
                        + "Forcing truncated single-pass merge instead of recursing further.",
                partials.size(), groups.size(), depth);
        String forcedJoin = groups.stream()
                .map(s -> "- " + s)
                .collect(Collectors.joining(System.lineSeparator()));
        String truncated = forcedJoin.length() > SUMMARIZER_CHUNK_BUDGET_CHARS
                ? forcedJoin.substring(0, SUMMARIZER_CHUNK_BUDGET_CHARS)
                : forcedJoin;
        return callSummarizer(systemPrompt,
                "The following are partial summaries of sections of the same document. Merge them into one concise summary.\n"
                        + truncated);
    }

    return mergePartialSummaries(systemPrompt, groups, depth + 1);
}

    // -------------------------------------------------------------------------
    // Classification on summary
    // -------------------------------------------------------------------------

    private JsonNode classifyFromSummary(String summary, String systemPrompt, boolean applyExternalTaxonomyNormalization)
            throws JsonProcessingException {
        if (StringUtils.isBlank(summary)) {
            log.warn("classifyFromSummary called with blank summary - returning empty result");
            return mapper.createObjectNode();
        }

        String userText = "Classify the following document summary and extract the required metadata.\n" + summary;

        Prompt prompt = Prompt.builder()
                .messages(
                        SystemMessage.builder().text(systemPrompt).build(),
                        UserMessage.builder().text(userText).build()
                )
                .build();

        String raw = chatClient.prompt(prompt).call().content();
        log.debug("Classification raw response: {}", raw);

        JsonNode parsed = convertToJSONNode(raw);
        return applyExternalTaxonomyNormalization ? normalizeClassificationResult(parsed) : parsed;
    }

    // -------------------------------------------------------------------------
    // Chunk selection
    // -------------------------------------------------------------------------

    private Optional<ElasticSearchService.PageBounds> waitForBounds(String documentId) throws IOException {
        for (int attempt = 1; attempt <= chunkFetchMaxRetries; attempt++) {
            Optional<ElasticSearchService.PageBounds> bounds = elasticSearchService.getPageBounds(documentId);
            if (bounds.isPresent()) {
                return bounds;
            }
            sleepBetweenRetries(attempt);
        }
        return Optional.empty();
    }

    private void sleepBetweenRetries(int completedAttempt) {
        if (completedAttempt < chunkFetchMaxRetries && chunkFetchDelayMs > 0) {
            try {
                Thread.sleep(chunkFetchDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for chunks", e);
            }
        }
    }

    private List<Document> selectRepresentativeChunks(
            String documentId,
            ElasticSearchService.PageBounds bounds,
            int middleCount,
            boolean includeKeywordChunks
    ) throws IOException {
        int firstPage = bounds.firstPage();
        int lastPage = bounds.lastPage();

        List<Document> begin = elasticSearchService.getChunksByStartPageAtMost(documentId, firstPage + 1);
        List<Document> end = elasticSearchService.getChunksByEndPageAtLeast(documentId, lastPage - 1);
        List<Document> interior = elasticSearchService.getInteriorChunks(documentId, firstPage + 1, lastPage - 1, 1000);
        List<Document> middle = pickEvenlyDistributed(interior, middleCount);
        List<Document> keyword = includeKeywordChunks
                ? elasticSearchService.searchKeywordChunks(documentId, KEYWORD_QUERY, 3)
                : List.of();

        LinkedHashMap<String, Document> dedup = new LinkedHashMap<>();
        addDistinct(dedup, begin);
        addDistinct(dedup, end);
        addDistinct(dedup, middle);
        addDistinct(dedup, keyword);

        return new ArrayList<>(dedup.values());
    }

    private void addDistinct(Map<String, Document> target, List<Document> docs) {
        for (Document d : docs) {
            String key = chunkKey(d);
            if (!target.containsKey(key)) {
                target.put(key, d);
            }
        }
    }

    private String chunkKey(Document d) {
        int start = pageInt(d, "startPage", Integer.MIN_VALUE);
        int end = pageInt(d, "endPage", Integer.MIN_VALUE);
        String text = StringUtils.defaultString(d.getText());
        return start + "|" + end + "|" + text.hashCode();
    }

    private int pageInt(Document d, String key, int fallback) {
        Object value = d.getMetadata().get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && s.matches("-?\\d+")) {
            return Integer.parseInt(s);
        }
        return fallback;
    }

    private List<Document> pickEvenlyDistributed(List<Document> interior, int requestedCount) {
        if (interior.isEmpty() || requestedCount <= 0) {
            return List.of();
        }

        List<Document> sorted = interior.stream()
                .sorted(Comparator.comparingInt(d -> pageInt(d, "startPage", Integer.MAX_VALUE)))
                .toList();

        int count = Math.min(requestedCount, sorted.size());
        if (count == sorted.size()) {
            return sorted;
        }

        Set<Integer> selectedIndices = new LinkedHashSet<>();
        if (count == 1) {
            selectedIndices.add(sorted.size() / 2);
        } else {
            for (int i = 0; i < count; i++) {
                int idx = (int) Math.round(i * (sorted.size() - 1.0) / (count - 1.0));
                selectedIndices.add(idx);
            }
        }

        List<Document> selected = new ArrayList<>(selectedIndices.size());
        for (Integer idx : selectedIndices) {
            selected.add(sorted.get(idx));
        }
        return selected;
    }

    // -------------------------------------------------------------------------
    // Prompt preparation - external taxonomy path
    // -------------------------------------------------------------------------

    private String prepareDocClassificationPrompt() throws JsonProcessingException {
        CachedPrompt local = cachedPrompt;
        if (local != null && local.isValid(promptCacheTtl)) {
            return local.value();
        }

        synchronized (this) {
            local = cachedPrompt;
            if (local != null && local.isValid(promptCacheTtl)) {
                return local.value();
            }

            String formatted = buildClassificationPrompt();
            cachedPrompt = new CachedPrompt(formatted, Instant.now());
            return formatted;
        }
    }

    private String buildClassificationPrompt() throws JsonProcessingException {
        SubjectsResponse subjectsResponse = externalAPIService.getSubjectsResponse();
        if (subjectsResponse == null || subjectsResponse.getDocumentTypeWiseSubjects() == null) {
            return appPrompt.getDocClassificationPrompt()
                    .replace("{DESCRIPTION_SECTION}", "[]")
                    + System.lineSeparator()
                    + appPrompt.getDocClassificationPromptResponseFormat();
        }

        List<CategoryRecord> records = subjectsResponse.getDocumentTypeWiseSubjects()
                .stream()
                .filter(Objects::nonNull)
                .map(dt -> new CategoryRecord(
                        dt.getDocumentType(),
                        dt.getSubjects() == null
                                ? List.of()
                                : dt.getSubjects().stream()
                                .filter(Objects::nonNull)
                                .map(SubjectsResponse.SubjectKeyValue::getKey)
                                .filter(Objects::nonNull)
                                .toList()
                ))
                .toList();

        String categoriesJson = mapper.writeValueAsString(records);

        return appPrompt.getDocClassificationPrompt()
                .replace("{DESCRIPTION_SECTION}", categoriesJson)
                + System.lineSeparator()
                + appPrompt.getDocClassificationPromptResponseFormat();
    }

    // -------------------------------------------------------------------------
    // Taxonomy normalisation - external taxonomy path
    // -------------------------------------------------------------------------

    private JsonNode normalizeClassificationResult(JsonNode root) {
        if (!(root instanceof ObjectNode rootObj)) {
            return root;
        }

        String predictedCategory = readText(root, "klassifikation", "hauptkategorie", "value");
        String predictedSubject = readText(root, "klassifikation", "unterkategorie", "value");

        if (StringUtils.isBlank(predictedCategory) && StringUtils.isBlank(predictedSubject)) {
            return root;
        }

        SubjectsResponse subjectsResponse = externalAPIService.getSubjectsResponse();
        if (subjectsResponse == null || subjectsResponse.getDocumentTypeWiseSubjects() == null) {
            return root;
        }

        TaxonomyMatch match = resolveTaxonomyMatch(
                predictedCategory,
                predictedSubject,
                subjectsResponse.getDocumentTypeWiseSubjects()
        );

        if (match == null) {
            return root;
        }

        if (StringUtils.isNotBlank(match.category())) {
            writeNestedValue(rootObj, match.category(), "klassifikation", "hauptkategorie", "value");
        }
        if (StringUtils.isNotBlank(match.subject())) {
            writeNestedValue(rootObj, match.subject(), "klassifikation", "unterkategorie", "value");
        }

        return rootObj;
    }

    private TaxonomyMatch resolveTaxonomyMatch(
            String predictedCategory,
            String predictedSubject,
            List<SubjectsResponse.DocumentTypeWiseSubjects> taxonomy
    ) {
        String category = findBestCategoryMatch(predictedCategory, taxonomy);
        if (StringUtils.isBlank(category)) {
            return inferBySubject(predictedSubject, taxonomy);
        }

        String subject = findBestSubjectMatch(category, predictedSubject, taxonomy);
        if (StringUtils.isNotBlank(subject)) {
            return new TaxonomyMatch(category, subject);
        }

        String single = findSingleSubject(category, taxonomy);
        return new TaxonomyMatch(category, single);
    }

    private TaxonomyMatch inferBySubject(
            String predictedSubject,
            List<SubjectsResponse.DocumentTypeWiseSubjects> taxonomy
    ) {
        if (StringUtils.isBlank(predictedSubject)) {
            return null;
        }

        String bestCategory = null;
        String bestSubject = null;
        int bestScore = -1;
        boolean ambiguous = false;

        for (SubjectsResponse.DocumentTypeWiseSubjects docType : taxonomy) {
            if (docType == null || StringUtils.isBlank(docType.getDocumentType()) || docType.getSubjects() == null) {
                continue;
            }

            for (SubjectsResponse.SubjectKeyValue kv : docType.getSubjects()) {
                if (kv == null) {
                    continue;
                }

                int score = Math.max(
                        matchScore(predictedSubject, kv.getKey()),
                        matchScore(predictedSubject, kv.getValue())
                );

                if (score <= 0) {
                    continue;
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestCategory = docType.getDocumentType();
                    bestSubject = kv.getKey();
                    ambiguous = false;
                } else if (score == bestScore && !equalsNormalized(bestCategory, docType.getDocumentType())) {
                    ambiguous = true;
                }
            }
        }

        if (bestScore <= 0 || ambiguous) {
            return null;
        }

        return new TaxonomyMatch(bestCategory, bestSubject);
    }

    private String findBestCategoryMatch(
            String predictedCategory,
            List<SubjectsResponse.DocumentTypeWiseSubjects> taxonomy
    ) {
        if (StringUtils.isBlank(predictedCategory)) {
            return null;
        }

        String best = null;
        int bestScore = -1;

        for (SubjectsResponse.DocumentTypeWiseSubjects docType : taxonomy) {
            if (docType == null || StringUtils.isBlank(docType.getDocumentType())) {
                continue;
            }

            int score = matchScore(predictedCategory, docType.getDocumentType());
            if (score > bestScore) {
                bestScore = score;
                best = docType.getDocumentType();
            }
        }

        return bestScore > 0 ? best : null;
    }

    private String findBestSubjectMatch(
            String category,
            String predictedSubject,
            List<SubjectsResponse.DocumentTypeWiseSubjects> taxonomy
    ) {
        if (StringUtils.isBlank(category) || StringUtils.isBlank(predictedSubject)) {
            return null;
        }

        SubjectsResponse.DocumentTypeWiseSubjects target = taxonomy.stream()
                .filter(dt -> dt != null && equalsNormalized(category, dt.getDocumentType()))
                .findFirst()
                .orElse(null);

        if (target == null || target.getSubjects() == null) {
            return null;
        }

        String best = null;
        int bestScore = -1;

        for (SubjectsResponse.SubjectKeyValue kv : target.getSubjects()) {
            if (kv == null) {
                continue;
            }

            int score = Math.max(
                    matchScore(predictedSubject, kv.getKey()),
                    matchScore(predictedSubject, kv.getValue())
            );

            if (score > bestScore) {
                bestScore = score;
                best = kv.getKey();
            }
        }

        return bestScore > 0 ? best : null;
    }

    private String findSingleSubject(
            String category,
            List<SubjectsResponse.DocumentTypeWiseSubjects> taxonomy
    ) {
        return taxonomy.stream()
                .filter(dt -> dt != null && equalsNormalized(category, dt.getDocumentType()))
                .findFirst()
                .map(SubjectsResponse.DocumentTypeWiseSubjects::getSubjects)
                .filter(subjects -> subjects != null && subjects.size() == 1)
                .map(subjects -> subjects.get(0))
                .map(SubjectsResponse.SubjectKeyValue::getKey)
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // Scoring helpers
    // -------------------------------------------------------------------------

    private int matchScore(String predicted, String candidate) {
        String left = normalize(predicted);
        String right = normalize(candidate);

        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        if (left.equals(right)) {
            return 100;
        }
        if (left.contains(right) || right.contains(left)) {
            return 70;
        }
        return 0;
    }

    private boolean equalsNormalized(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "")
                .trim();
    }

    // -------------------------------------------------------------------------
    // JSON helpers
    // -------------------------------------------------------------------------

    private String readText(JsonNode root, String... path) {
        JsonNode node = traversePath(root, path);
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private double readDouble(JsonNode root, String... path) {
        JsonNode node = traversePath(root, path);
        return node == null || node.isNull() ? 0.0d : node.asDouble(0.0d);
    }

    private JsonNode traversePath(JsonNode node, String... path) {
        for (String segment : path) {
            if (node == null) {
                return null;
            }
            node = node.get(segment);
        }
        return node;
    }

    private void writeNestedValue(ObjectNode root, String value, String... path) {
        if (path.length == 0) {
            return;
        }

        ObjectNode current = root;
        for (int i = 0; i < path.length - 1; i++) {
            String segment = path[i];
            JsonNode child = current.get(segment);
            if (!(child instanceof ObjectNode)) {
                child = mapper.createObjectNode();
                current.set(segment, child);
            }
            current = (ObjectNode) child;
        }

        if (value == null) {
            current.putNull(path[path.length - 1]);
        } else {
            current.put(path[path.length - 1], value);
        }
    }

    private void writeNestedValue(ObjectNode root, double value, String... path) {
        if (path.length == 0) {
            return;
        }

        ObjectNode current = root;
        for (int i = 0; i < path.length - 1; i++) {
            String segment = path[i];
            JsonNode child = current.get(segment);
            if (!(child instanceof ObjectNode)) {
                child = mapper.createObjectNode();
                current.set(segment, child);
            }
            current = (ObjectNode) child;
        }

        current.put(path[path.length - 1], value);
    }

    private void writeStringList(ObjectNode root, List<String> values, String... path) {
        if (path.length == 0) {
            return;
        }

        ObjectNode current = root;
        for (int i = 0; i < path.length - 1; i++) {
            String segment = path[i];
            JsonNode child = current.get(segment);
            if (!(child instanceof ObjectNode)) {
                child = mapper.createObjectNode();
                current.set(segment, child);
            }
            current = (ObjectNode) child;
        }

        ArrayNode arrayNode = mapper.createArrayNode();
        if (values != null) {
            values.stream()
                    .filter(Objects::nonNull)
                    .forEach(arrayNode::add);
        }

        current.set(path[path.length - 1], arrayNode);
    }

    private JsonNode convertToJSONNode(String text) {
        try {
            String cleaned = text == null
                    ? ""
                    : text.replace("```json", "").replace("```", "").trim();
            return mapper.readTree(cleaned);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse generated JSON: {}", text, e);
            throw new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // Inner records
    // -------------------------------------------------------------------------

    private record CategoryRecord(String category, List<String> subCategories) {
    }

    private record CachedPrompt(String value, Instant createdAt) {
        boolean isValid(Duration ttl) {
            return createdAt.plus(ttl).isAfter(Instant.now());
        }
    }

    private record TaxonomyMatch(String category, String subject) {
    }
}