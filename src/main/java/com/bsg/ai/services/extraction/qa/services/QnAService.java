package com.bsg.ai.services.extraction.qa.services;

import com.bsg.ai.services.config.NoAnswerHistoryPrunerAdvisor;
import com.bsg.ai.services.config.QueryLoggingAdvisor;
import com.bsg.ai.services.config.WindowedCompressionQueryTransformer;
import com.bsg.ai.services.util.SmartStreamBuffer;
import com.bsg.ai.services.extraction.multiDocQa.RagProperties;
import com.bsg.ai.services.extraction.multiDocQa.services.*;
import com.bsg.ai.services.extraction.qa.records.QnAResult;
import com.bsg.ai.services.extraction.services.service.ElasticSearchService;
import com.bsg.ai.services.config.ApplicationPrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class QnAService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    /**
     * RAG advisor used for single-turn methods (ask, askAboutDocument, askWithSources).
     * No query transformation — the user's question is used verbatim for retrieval.
     */
    private final RetrievalAugmentationAdvisor ragAdvisor;

    /**
     * RAG advisor used for multi-turn Q&A (askWithHistory).
     * Adds {@link CompressionQueryTransformer} which rewrites follow-up questions into
     * self-contained queries before retrieval. Is a no-op when query history is empty
     * (first question in a session), so no extra LLM call is made for the first turn.
     */
    private final RetrievalAugmentationAdvisor historyRagAdvisor;

    private final Advisor multiDocRagAdvisor;
//    private final HttpRerankerDocumentPostProcessor reranker;

    /**
     * Stateless advisor — holds only the shared {@link ChatMemory} reference and an
     * immutable stemmed-phrase set.  Created once here so it is not re-allocated on
     * every request; the expensive Lucene stemming already runs once at class load.
     */
    private final NoAnswerHistoryPrunerAdvisor noAnswerPrunerAdvisor;

    @Value("${qna.clarifying-followup.enabled:false}")
    private boolean clarifyingFollowupEnabled;

    public QnAService(ChatClient.Builder builder, HybridElasticsearchVectorStore vectorStore, RagProperties props,
                      HttpRerankerDocumentPostProcessor reranker, FactCompressingDocumentPostProcessor factCompressor,
                      ElasticSearchService elasticSearchService, ApplicationPrompt appPrompt) {
//        this.reranker = reranker;
        this.appPrompt = appPrompt;
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(6)
                .build();

        this.noAnswerPrunerAdvisor = new NoAnswerHistoryPrunerAdvisor(chatMemory);

        // WindowedCompressionQueryTransformer is built BEFORE the main chatClient is
        // configured on the shared builder. It calls builder.build() internally at
        // construction time, capturing a plain ChatClient with no RAG advisors — which
        // prevents the condensation LLM call from re-entering the RAG pipeline.
        WindowedCompressionQueryTransformer compressionTransformer = WindowedCompressionQueryTransformer.builder()
                .chatClientBuilder(builder)
                .maxHistoryTurns(3)
                .build();

        VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(props.retriever().topK())
                .build();

        VectorStoreDocumentRetriever multiDocRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(Math.max(props.retriever().topK(), props.context().multiDocTopK()))
                .build();

        this.ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .build();

this.historyRagAdvisor = RetrievalAugmentationAdvisor.builder()
        .queryTransformers(compressionTransformer)
        .documentRetriever(retriever)
        .documentJoiner(new SmartDocumentJoiner(props.retriever().topK(), elasticSearchService))
        .documentPostProcessors(reranker)
        .build();

        this.chatClient = builder
                .defaultSystem("""
                        You are a precise document Q&A assistant.
                        Answer ONLY from the context provided. Provide answer in detail rather then concise.
                        When responding, use Markdown formatting to structure your answer clearly:
                         - Use ## and ### for section headings
                         - Use **bold** for key terms and important values
                         - Use bullet lists (-) for enumerations and numbered lists (1.) for sequential steps
                         - Use inline `code` for identifiers, field names, and short values
                         - Use fenced code blocks (```) for multi-line code or JSON samples
                         - Use tables for comparisons or structured data
                         - Use > blockquotes for key takeaways or important notes
                         - Use --- horizontal rules to separate major sections
                         Keep responses structured detail. Prefer structured output over long prose paragraphs.
                        If the answer is not in the context, say "I don't have enough information to answer that."
                        Be concise and cite which part of the document your answer comes from.
                        """)
                .defaultAdvisors(new QueryLoggingAdvisor())
                .build();

        List<QueryTransformer> transformers = new ArrayList<>();

        if (props.query().rewriteEnabled()) {
            transformers.add(
                    StoringRewriteQueryTransformer.builder()
                            .delegate(
                                    RewriteQueryTransformer.builder()
                                            .promptTemplate(PromptTemplate.builder().template("""
                                                Given a user query, rewrite it to provide better results when querying a {target}.
                                                Remove any irrelevant information, and ensure the query is concise and specific.
                                                Return only one improved query.
                                                Original query:
                                                {query}
                                                Rewritten query:
                                                """).build())
                                            .chatClientBuilder(builder.build().mutate())
                                            .build())
                            .build());
        }

        // ── Generation: Augmenter ────────────────────────────────────────────
        ContextualQueryAugmenter augmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)   // refuse to hallucinate when no docs found
                .build();

        // ── Assemble the Advisor ─────────────────────────────────────────────
        RetrievalAugmentationAdvisor.Builder queryAugmenter = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(transformers)
                .documentRetriever(multiDocRetriever)
                .documentJoiner(new SmartDocumentJoiner(props.context().maxContextChunks(), elasticSearchService))
                .documentPostProcessors(reranker, factCompressor)
                .queryAugmenter(augmenter);
        if (props.query().expandEnabled()) {
            // ── Pre-Retrieval: Query Expander ────────────────────────────────────
            MultiQueryExpander expander = MultiQueryExpander.builder()
                    .chatClientBuilder(builder.build().mutate())
                    .numberOfQueries(props.query().expandCount())
                    .includeOriginal(true)   // always include the original query too
                    .build();
            queryAugmenter.queryExpander(StoringMultiQueryExpander.builder().delegate(expander).build());
        }
        this.multiDocRagAdvisor = queryAugmenter.build();
    }

    // -------------------------------------------------------------------------
    // Scoped Q&A — restrict retrieval to a specific document.
    // Streams tokens to the client as they arrive from the LLM.
    // -------------------------------------------------------------------------

    public Flux<String> askAboutDocument(String question, List<String> documentIds) {
        if (documentIds != null && documentIds.size() > 1) {
            return askMultiDoc(question, documentIds, null);
        }
        return chatClient
                .prompt()
                .advisors(ragAdvisor)
                .advisors(a -> a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                        buildFilterExpression(documentIds, null)))
                .user(question)
                .stream()
                .content()
                .transform(new SmartStreamBuffer());
    }

    // -------------------------------------------------------------------------
    // Rich response — returns answer + the source chunks that were used.
    // Retrieved documents are stored in ChatClientResponse.context() by
    // RetrievalAugmentationAdvisor under DOCUMENT_CONTEXT.
    // -------------------------------------------------------------------------

    public QnAResult askWithSources(String question, List<String> documentIds) {
        ChatClientResponse clientResponse = chatClient
                .prompt()
                .advisors(ragAdvisor)
                .advisors(a -> a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                        buildFilterExpression(documentIds, null)))
                .user(question)
                .call()
                .chatClientResponse();

        String answer = clientResponse.chatResponse().getResult().getOutput().getText();
        List<String> sources = extractSources(clientResponse);
        return new QnAResult(question, answer, sources);
    }

    // -------------------------------------------------------------------------
    // Multi-turn Q&A — maintains conversation history across questions.
    //
    // Advisor execution order:
    //   1. MessageChatMemoryAdvisor  (HIGHEST_PRECEDENCE + 1000) — prepends stored
    //                                  history to the prompt messages
    //   2. historyRagAdvisor         (order = 0) — CompressionQueryTransformer rewrites
    //                                  the follow-up into a standalone query using that
    //                                  history; VectorStoreDocumentRetriever retrieves
    //                                  chunks on the rewritten query
    //   3. QueryLoggingAdvisor       (LOWEST_PRECEDENCE - 1) — logs the final
    //                                  augmented prompt + token usage (default advisor)
    // -------------------------------------------------------------------------

    public Flux<String> askWithHistory(String question, List<String> documentIds, String conversationId) {
        return chatClient
                .prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                        .conversationId(conversationId)
                        .build())
                .advisors(noAnswerPrunerAdvisor)
                .advisors(historyRagAdvisor)
                .advisors(a -> a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                        buildFilterExpression(documentIds, null)))
                .user(question)
                .stream()
                .content()
                .transform(new SmartStreamBuffer());
    }

    // ── Streaming ─────────────────────────────────────────────────────────────

    /**
     * Multi-doc streaming QA — mirrors your existing single-doc pattern:
     * <pre>
     *   qnAService.askAboutDocument(question, documentId)
     * </pre>
     *
     * Streams token-by-token through the full RAG pipeline
     * (query rewrite → expand → ES retrieval → rerank → LLM stream).
     *
     * Supports optional scoping:
     * @param question        the user question
     * @param documentIds     null/empty = search all docs; otherwise scoped to these IDs
     * @param filterExpression optional raw Spring AI filter (e.g. {@code "category == 'legal'"})
     * @return Flux of token strings, suitable for SSE or WebFlux streaming
     */
    public Flux<String> askMultiDoc(String question, List<String> documentIds, String filterExpression) {
        log.info("Streaming multi-doc QA — question: '{}', docIds: {}", question, documentIds);
        String builtFilter = buildFilterExpression(documentIds, filterExpression);

        var callSpec = chatClient
                .prompt()
                .advisors(multiDocRagAdvisor)
                .user(question);

        if (builtFilter != null && !builtFilter.isBlank()) {
            callSpec = callSpec.advisors(a ->
                    a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, builtFilter));
        }

        return callSpec
                .stream()
                .content(); // Flux<String> — one element per token
    }

    // ── Blocking (with provenance) ────────────────────────────────────────────

    /**
     * Blocking multi-doc QA
     * reranked source references. Use when the client needs provenance data
     * alongside the answer (e.g. citation UI).
     */
    public String answer(String question, List<String> documentIds, String filterExpression) {
        long start = System.currentTimeMillis();
        log.info("Blocking multi-doc QA — question: '{}'", question);

        String builtFilter = buildFilterExpression(documentIds, filterExpression);

        var callSpec = chatClient
                .prompt()
                .advisors(multiDocRagAdvisor)
                .user(question);

        if (builtFilter != null) {
            callSpec = callSpec.advisors(a ->
                    a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, builtFilter));
        }

        var chatResponse = callSpec.call().chatResponse();

        String answer = chatResponse.getResult().getOutput().getText();
        long latency = System.currentTimeMillis() - start;

        log.info("Answered in {}ms", latency);

        return answer;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
//
//    private String buildDocumentFilter(String documentId) {
//        String escapedId = escapeLucene(documentId == null ? "" : documentId);
//        return "documentId.keyword == '" + escapedId + "'";
//    }

    /**
     * Merges a list of document IDs and/or a free-form filter expression into
     * a single Spring AI portable filter expression string.
     *
     * Examples:
     * <pre>
     *   ["doc-1"]                       → "documentId == 'doc-1'"
     *   ["doc-1","doc-2"]               → "documentId in ['doc-1', 'doc-2']"
     *   [] + "category == 'legal'"      → "category == 'legal'"
     *   ["doc-1"] + "category == 'hr'"  → "documentId == 'doc-1' && (category == 'hr')"
     * </pre>
     */
    private String buildFilterExpression(List<String> documentIds, String filterExpression) {
        List<String> clauses = new ArrayList<>();

        if (documentIds != null && !documentIds.isEmpty()) {
            if (documentIds.size() == 1) {
                clauses.add("documentId.keyword == '" + escapeLucene(documentIds.get(0)) + "'");
            } else {
                String ids = documentIds.stream()
                        .map(id -> "'" + escapeLucene(id) + "'")
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                clauses.add("documentId.keyword in [" + ids + "]");
            }
        }

        if (filterExpression != null && !filterExpression.isBlank()) {
            clauses.add("(" + filterExpression + ")");
        }

        return clauses.isEmpty() ? "" : String.join(" && ", clauses);
    }

    /**
     * Escape Lucene query-string special characters because Elasticsearch vector-store
     * filter conversion uses query-string syntax under the hood.
     */
    private String escapeLucene(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("&&", "\\&&")
                .replace("||", "\\||")
                .replace("!", "\\!")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("^", "\\^")
                .replace("\"", "\\\"")
                .replace("~", "\\~")
                .replace("*", "\\*")
                .replace("?", "\\?")
                .replace(":", "\\:")
                .replace("/", "\\/");
    }

    private List<String> extractSources(ChatClientResponse clientResponse) {
        List<String> sources = new ArrayList<>();
        Object retrieved = clientResponse.context().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        if (retrieved instanceof List<?> docs) {
            for (Object doc : docs) {
                if (doc instanceof Document d) {
                    String page  = String.valueOf(d.getMetadata().getOrDefault("page_number", "?"));
                    String chunk = String.valueOf(d.getMetadata().getOrDefault("chunk_index", "?"));
                    String snippet = d.getFormattedContent().length() > 120
                            ? d.getFormattedContent().substring(0, 120) + "..."
                            : d.getFormattedContent();
                    sources.add("Page " + page + ", chunk " + chunk + ": " + snippet);
                }
            }
        }
        return sources;
    }
    
public record SourceReference(
    Integer startPage,
    Integer endPage,
    String section,
    String sourceFile,
    String documentId,
    String chunkId,
    String snippet
) {}
public record QnAWithReferences(String answer, List<SourceReference> references, List<String> suggestedFollowUps) {}

public QnAWithReferences askWithHistorySync(String question, List<String> documentIds, String conversationId) {
    
    ChatClientResponse clientResponse = chatClient
            .prompt()
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                    .conversationId(conversationId)
                    .build())
            .advisors(noAnswerPrunerAdvisor)
            .advisors(historyRagAdvisor)
            .advisors(a -> a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                    buildFilterExpression(documentIds, null)))
            .user(question)
            .call()
            .chatClientResponse();

    String answer = clientResponse.chatResponse().getResult().getOutput().getText();
    List<SourceReference> references = extractReferences(clientResponse);

    List<String> followUps = List.of();
    if (clarifyingFollowupEnabled && answer != null && noAnswerPrunerAdvisor.containsNoAnswer(answer)) {
        followUps = generateClarifyingFollowUps(question, references);
    }

    return new QnAWithReferences(answer, references, followUps);
}

private final ApplicationPrompt appPrompt; 
private List<String> generateClarifyingFollowUps(String originalQuestion, List<SourceReference> references) {
    try {
        String detectedLanguage = detectLanguage(originalQuestion);

        String contextHint = references.isEmpty()
                ? "No related content was found in the document."
                : "Related but insufficient content was found, touching on: "
                        + references.stream().map(SourceReference::snippet).limit(3)
                                .collect(java.util.stream.Collectors.joining(" | "));

        String systemPrompt = appPrompt.getQnaClarifyingFollowupPrompt()
                .replace("{DETECTED_LANGUAGE}", detectedLanguage);
        String userPrompt = "Original question: " + originalQuestion + "\n" + contextHint;

        String raw = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        List<String> candidates = raw.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .filter(line -> line.endsWith("?"))
                .limit(2)
                .toList();

        return candidates.stream()
                .filter(line -> matchesDetectedLanguage(line, detectedLanguage))
                .toList();

    } catch (Exception e) {
        log.warn("Failed to generate clarifying follow-ups: {}", e.getMessage());
        return List.of();
    }
}

/**
 * Detects the language of the user's question via a single cheap, narrowly-scoped LLM
 * call (just the language name, nothing else). This is deliberately separated from the
 * main follow-up generation call - asking a small model to juggle language detection AND
 * grounding AND format AND brevity all in one call proved unreliable (it sometimes drifted
 * into the document's language or a third language entirely). Isolating detection into its
 * own trivial call makes each individual call simpler and easier for the model to get right.
 */
private String detectLanguage(String question) {
    try {
        String raw = chatClient.prompt()
                .system(appPrompt.getQnaLanguageDetectionPrompt())
                .user(question)
                .call()
                .content();
        String detected = raw == null ? "" : raw.strip();
        return detected.isBlank() ? "English" : detected;
    } catch (Exception e) {
        log.warn("Language detection failed, defaulting to English: {}", e.getMessage());
        return "English";
    }
}

/**
 * Lightweight verification that a generated follow-up is actually in the detected language,
 * using script/character-set heuristics rather than another LLM call (keeps this cheap).
 * Currently distinguishes Latin-script languages from a few common non-Latin scripts; if the
 * detected language is Latin-script (English, German, French, Spanish, etc.) and the follow-up
 * contains non-Latin characters (Cyrillic, CJK, etc.), it's rejected as a mismatch.
 */
private boolean matchesDetectedLanguage(String text, String detectedLanguage) {
    boolean detectedIsLatinScript = !detectedLanguage.matches(
            "(?i).*(chinese|japanese|korean|russian|arabic|hebrew|greek|thai|hindi).*");
    boolean textHasNonLatinChars = text.codePoints()
            .anyMatch(cp -> cp > 0x24F && !Character.isWhitespace(cp)
                    && cp != '?' && cp != '¿' && cp != '¡'); // allow Spanish punctuation as harmless
    return !(detectedIsLatinScript && textHasNonLatinChars);
}
private List<SourceReference> extractReferences(ChatClientResponse clientResponse) {
    List<SourceReference> references = new ArrayList<>();
    Object retrieved = clientResponse.context().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
    if (retrieved instanceof List<?> docs) {
        int limit = Math.min(docs.size(), 4);
        for (Object doc : docs.subList(0, limit)) {
            if (doc instanceof Document d) {
                var meta = d.getMetadata();

                Integer startPage = parseIntSafe(meta.get("startPage"));
                Integer endPage = parseIntSafe(meta.get("endPage"));
                String section = String.valueOf(meta.getOrDefault("section", ""));
                String sourceFile = String.valueOf(meta.getOrDefault("sourceFile", ""));
                String documentId = String.valueOf(meta.getOrDefault("documentId", ""));
                String chunkId = meta.get("documentId") + "_C" + meta.getOrDefault("chunkIndex", "0");

                String content = d.getText();  // raw chunk text, not getFormattedContent()
                String snippet = content == null ? "" :
                        (content.length() > 220 ? content.substring(0, 220) + "..." : content);

                references.add(new SourceReference(startPage, endPage, section, sourceFile, documentId, chunkId, snippet));
            }
        }
    }
    return references;
}
private Integer parseIntSafe(Object value) {
    try {
        return value == null ? 0 : Integer.valueOf(String.valueOf(value));
    } catch (NumberFormatException e) {
        return 0;
    }
}

}

