package com.bsg.ai.services.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@link QueryTransformer} that compresses a windowed slice of conversation history
 * and a follow-up query into a self-contained standalone query before RAG retrieval.
 *
 * <p>Mirrors the behaviour of {@code CompressionQueryTransformer} (which is {@code final}
 * and cannot be subclassed) but caps the history fed to the LLM at the last
 * {@code maxHistoryTurns} Q&amp;A turns. One turn = one USER message + one ASSISTANT
 * message. This bounds the token cost of the condensation call regardless of how long the
 * overall session grows.
 *
 * <h2>Fast path</h2>
 * If {@code query.history()} is empty (first question in a session) the transformer
 * returns the query unchanged — no LLM call is made.
 *
 * <h2>Failure safety</h2>
 * If the condensation call returns blank output, the original query is returned so the
 * overall RAG request still completes with potentially degraded retrieval.
 */
public class WindowedCompressionQueryTransformer implements QueryTransformer {

    private static final Logger log = LoggerFactory.getLogger(WindowedCompressionQueryTransformer.class);

    public static final int DEFAULT_MAX_HISTORY_TURNS = 3;

    // Same template as CompressionQueryTransformer to keep compression behaviour identical.
    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
            Given the following conversation history and a follow-up query, your task is to synthesize
            a concise, standalone query that incorporates the context from the history.
            Ensure the standalone query is clear, specific, and maintains the user's intent.
            Do NOT include source references, document names, or section hints in the standalone query.
            Output only the user's information need as a clean, self-contained question.
            
            Conversation history:
            {history}

            Follow-up query:
            {query}

            Standalone query:
            """);

    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;
    private final int maxHistoryTurns;

    private WindowedCompressionQueryTransformer(Builder builder) {
        this.chatClient    = builder.chatClientBuilder.build();
        this.promptTemplate = builder.promptTemplate != null ? builder.promptTemplate : DEFAULT_PROMPT_TEMPLATE;
        this.maxHistoryTurns = builder.maxHistoryTurns;
    }

    @Override
    public Query transform(Query query) {
        Assert.notNull(query, "query cannot be null");

        if (query.history().isEmpty()) {
            log.debug("[WindowedCompression] No history — passing query through unchanged.");
            return query;
        }

        String windowedHistory = buildWindowedHistory(query.history());

        log.debug("[WindowedCompression] Compressing with last {} turn(s) of history.", maxHistoryTurns);

        String compressed = chatClient.prompt()
                .user(u -> u.text(promptTemplate.getTemplate())
                        .param("history", windowedHistory)
                        .param("query", query.text()))
                .call()
                .content();

        if (!StringUtils.hasText(compressed)) {
            log.warn("[WindowedCompression] Compression returned empty — using original query.");
            return query;
        }

        log.info("[WindowedCompression] \"{}\" → \"{}\"", query.text(), compressed.trim());
        return query.mutate().text(compressed.trim()).build();
    }

    /**
     * Filters to USER/ASSISTANT messages only, then keeps the last
     * {@code maxHistoryTurns * 2} entries — the minimal context for compression
     * without sending the entire session to the LLM.
     */
    private String buildWindowedHistory(List<Message> history) {
        List<Message> turns = history.stream()
                .filter(m -> m.getMessageType() == MessageType.USER
                          || m.getMessageType() == MessageType.ASSISTANT)
                .collect(Collectors.toList());

        // Drop the last USER message — it is query.text() itself and is already
        // passed separately as the {query} placeholder in the compression prompt.
        if (!turns.isEmpty() && turns.get(turns.size() - 1).getMessageType() == MessageType.USER) {
            turns = turns.subList(0, turns.size() - 1);
        }

        int cap = maxHistoryTurns * 2;
        if (turns.size() > cap) {
            turns = turns.subList(turns.size() - cap, turns.size());
        }

        return turns.stream()
                .map(m -> "%s: %s".formatted(m.getMessageType(), m.getText()))
                .collect(Collectors.joining("\n"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private ChatClient.Builder chatClientBuilder;
        private PromptTemplate promptTemplate;
        private int maxHistoryTurns = DEFAULT_MAX_HISTORY_TURNS;

        private Builder() {}

        public Builder chatClientBuilder(ChatClient.Builder chatClientBuilder) {
            this.chatClientBuilder = chatClientBuilder;
            return this;
        }

        public Builder maxHistoryTurns(int maxHistoryTurns) {
            Assert.isTrue(maxHistoryTurns >= 1, "maxHistoryTurns must be >= 1, got: " + maxHistoryTurns);
            this.maxHistoryTurns = maxHistoryTurns;
            return this;
        }

        public Builder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public WindowedCompressionQueryTransformer build() {
            Assert.notNull(chatClientBuilder, "chatClientBuilder cannot be null");
            return new WindowedCompressionQueryTransformer(this);
        }
    }
}
