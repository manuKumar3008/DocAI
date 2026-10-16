package com.bsg.ai.services.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.core.Ordered;

import java.util.List;

/**
 * Advisor that logs every LLM interaction at two levels:
 *
 * <p><b>INFO</b> — one summary line per call:
 * <pre>
 * [LLM] query="What is the invoice total?" | elapsed=843ms | prompt=1420 completion=87 total=1507 tokens
 * </pre>
 *
 * <p><b>DEBUG</b> — the exact messages sent to the LLM (enriched by RAG advisors) and the
 * full response text. Enable with:
 * <pre>
 * logging.level.com.bsg.ai.services.config.QueryLoggingAdvisor: DEBUG
 * </pre>
 *
 * <p>This advisor runs just before {@code ChatModelCallAdvisor} (order =
 * {@code Ordered.LOWEST_PRECEDENCE - 1}), so by the time {@code adviseCall} is invoked
 * the prompt has already been augmented with RAG context by
 * {@link org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor}.
 * The messages logged at DEBUG are therefore the <em>exact</em> payload forwarded to the
 * model. Using {@code Integer.MAX_VALUE} would collide with {@code ChatModelCallAdvisor}
 * (which also uses {@code Ordered.LOWEST_PRECEDENCE}) causing this advisor to be sorted
 * after it and never invoked.
 */
public class QueryLoggingAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(QueryLoggingAdvisor.class);

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String query = extractLastUserMessage(request);

        logPrompt(request.prompt().getInstructions(), query);

        long start = System.currentTimeMillis();
        ChatClientResponse response = chain.nextCall(request);
        long elapsed = System.currentTimeMillis() - start;

        logResponse(query, elapsed, response);
        return response;
    }

    // -------------------------------------------------------------------------

    private void logPrompt(List<Message> messages, String query) {
        log.info("[LLM] >>> query=\"{}\" | messages={}", query, messages.size());

        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder("[LLM] >>> exact prompt sent to model:\n");
            for (Message msg : messages) {
                sb.append("  [").append(msg.getMessageType().getValue().toUpperCase()).append("] ")
                  .append(msg.getText())
                  .append("\n");
            }
            log.debug(sb.toString());
        }
    }

    private void logResponse(String query, long elapsed, ChatClientResponse response) {
        if (response.chatResponse() == null || response.chatResponse().getMetadata() == null) {
            log.info("[LLM] <<< query=\"{}\" | elapsed={}ms | token usage unavailable", query, elapsed);
            return;
        }

        Usage usage      = response.chatResponse().getMetadata().getUsage();
        Integer prompt     = usage.getPromptTokens();
        Integer completion = usage.getCompletionTokens();
        Integer total      = usage.getTotalTokens();

        log.info("[LLM] <<< query=\"{}\" | elapsed={}ms | prompt={} completion={} total={} tokens",
                query, elapsed,
                prompt     != null ? prompt     : "?",
                completion != null ? completion : "?",
                total      != null ? total      : "?");

        if (log.isDebugEnabled() && response.chatResponse().getResult() != null) {
            String responseText = response.chatResponse().getResult().getOutput().getText();
            log.debug("[LLM] <<< model response:\n  {}", responseText);
        }
    }

    private String extractLastUserMessage(ChatClientRequest request) {
        return request.prompt().getInstructions().stream()
                .filter(m -> m.getMessageType().getValue().equalsIgnoreCase("user"))
                .map(Message::getText)
                .reduce((first, last) -> last)
                .orElse("<no user message>");
    }

    @Override
    public String getName() {
        return QueryLoggingAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        // One step before ChatModelCallAdvisor (which uses Ordered.LOWEST_PRECEDENCE =
        // Integer.MAX_VALUE and terminates the chain without calling nextCall).
        // This ensures we run after RAG enrichment but still reach the LLM call.
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
