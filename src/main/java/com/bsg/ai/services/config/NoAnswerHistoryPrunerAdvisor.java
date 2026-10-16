package com.bsg.ai.services.config;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Removes the last USER+ASSISTANT turn from {@link ChatMemory} when the LLM signals it
 * could not find a relevant answer in the retrieved context.
 *
 * <h2>Why this is needed</h2>
 * A "no context found" exchange pollutes future compression: the
 * {@link WindowedCompressionQueryTransformer} would incorporate a useless Q&A pair when
 * rewriting the next follow-up question. Pruning it keeps the windowed history clean.
 *
 * <h2>Advisor ordering</h2>
 * Order is set to {@code HIGHEST_PRECEDENCE + 500}, which is strictly lower than
 * {@link org.springframework.ai.chat.client.advisor.api.Advisor#DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER}
 * ({@code HIGHEST_PRECEDENCE + 1000}). Because the advisor chain unwinds after() from
 * innermost to outermost, this advisor's after() fires <em>after</em>
 * {@code MessageChatMemoryAdvisor} has already persisted the turn — so we can surgically
 * remove it from memory.
 *
 * <h2>Streaming</h2>
 * {@link #adviseStream} is overridden to use {@link ChatClientMessageAggregator}, which
 * assembles the full assistant response in a side-channel while still passing individual
 * tokens downstream. The pruning side-effect runs on the aggregated response.
 *
 * <h2>Phrase matching</h2>
 * At startup, each configured phrase is also stemmed via Lucene's {@link EnglishAnalyzer}
 * (Porter stemmer, no stop-word removal) and added to the match set.  At runtime the LLM
 * response is only lowercased — no analysis — keeping the hot path lightweight.  The
 * stemmed variants catch inflected forms the LLM might produce (e.g. "informations",
 * "mentioning") that would otherwise slip past an exact-string check.
 */
public class NoAnswerHistoryPrunerAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(NoAnswerHistoryPrunerAdvisor.class);

    public static final String NO_ANSWER_FLAG = "no_answer_in_context";

    // Must be strictly less than DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER (HIGHEST_PRECEDENCE + 1000)
    // so our after() runs after MessageChatMemoryAdvisor has saved the turn.
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 500;

    private static final Set<String> DEFAULT_NO_ANSWER_PHRASES = Set.of(
            "i don't have enough information to answer",
            "i don't have enough information",
            "not enough information",
            "i don't know",
            "no information available",
            "cannot find",
            "not found in the context",
            "not in the context",
            "not mentioned in the document",
            "not covered in the provided"
    );

    // Stemmed once at class load; matched against the lowercased (not stemmed) response.
    // Using EnglishAnalyzer with an empty stop-word set so tokens like "not", "no", "in"
    // are preserved — they carry meaning in these phrases.
    static final Set<String> DEFAULT_STEMMED_PHRASES =
            DEFAULT_NO_ANSWER_PHRASES.stream()
                    .map(NoAnswerHistoryPrunerAdvisor::stemText)
                    .collect(Collectors.toUnmodifiableSet());

    private final ChatMemory chatMemory;
    // Union of caller-supplied phrases and their stemmed forms; built once at construction.
    private final Set<String> allPhrases;

    public NoAnswerHistoryPrunerAdvisor(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        this.allPhrases = union(DEFAULT_NO_ANSWER_PHRASES, DEFAULT_STEMMED_PHRASES);
    }

    public NoAnswerHistoryPrunerAdvisor(ChatMemory chatMemory, Set<String> noAnswerPhrases) {
        this.chatMemory = chatMemory;
        Set<String> stemmed = noAnswerPhrases.stream()
                .map(NoAnswerHistoryPrunerAdvisor::stemText)
                .collect(Collectors.toSet());
        this.allPhrases = union(noAnswerPhrases, stemmed);
    }
    public boolean containsNoAnswer(String responseText) {   // was: private boolean containsNoAnswer(...)
    String lower = responseText.toLowerCase();
    return allPhrases.stream().anyMatch(lower::contains);
}
    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        if (response.chatResponse() == null || response.chatResponse().getResult() == null) {
            return response;
        }
        String text = response.chatResponse().getResult().getOutput().getText();
        if (text == null) return response;
        if (containsNoAnswer(text)) {
            Object id = response.context().get(ChatMemory.CONVERSATION_ID);
            pruneLastTurn(id != null ? id.toString() : ChatMemory.DEFAULT_CONVERSATION_ID);
        }
        return response;
    }

    // Override so we get the fully assembled text, not just the last streaming chunk.
    // Tokens continue to flow downstream as normal; pruning is a side-effect on completion.
    // conversationId is captured from the request here because ChatClientMessageAggregator
    // builds the aggregated response from token chunks and does not copy the request context
    // (including CONVERSATION_ID) into the aggregated ChatClientResponse.
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        String conversationId = resolveConversationId(request);
        return Mono.just(request)
                .publishOn(getScheduler())
                .map(r -> this.before(r, chain))
                .flatMapMany(chain::nextStream)
                .transform(flux -> new ChatClientMessageAggregator()
                        .aggregateChatClientResponse(flux, aggregated -> afterWithId(aggregated, conversationId)));
    }

   

    /**
     * Tokenises and Porter-stems {@code text} using {@link EnglishAnalyzer} with an empty
     * stop-word set so that every token (including "no", "not", "in") is preserved.
     * Tokens are joined with a single space, producing a normalised string that can be
     * compared with {@link String#contains}.
     */
    static String stemText(String text) {
        try (EnglishAnalyzer analyzer = new EnglishAnalyzer(CharArraySet.EMPTY_SET)) {
            TokenStream ts = analyzer.tokenStream("", text);
            CharTermAttribute termAttr = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            StringBuilder sb = new StringBuilder();
            while (ts.incrementToken()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(termAttr);
            }
            ts.end();
            return sb.toString();
        } catch (IOException e) {
            // Unreachable for in-memory analysis, but fall back gracefully.
            return text.toLowerCase();
        }
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.addAll(b);
        return Set.copyOf(result);
    }

    private String resolveConversationId(ChatClientRequest request) {
        Object id = request.context().get(ChatMemory.CONVERSATION_ID);
        return id != null ? id.toString() : ChatMemory.DEFAULT_CONVERSATION_ID;
    }

    private void afterWithId(ChatClientResponse response, String conversationId) {
        if (response.chatResponse() == null || response.chatResponse().getResult() == null) {
            return;
        }
        String text = response.chatResponse().getResult().getOutput().getText();
        if (text == null) return;
        if (containsNoAnswer(text)) {
            pruneLastTurn(conversationId);
        }
    }

    private void pruneLastTurn(String conversationId) {
        List<Message> messages = chatMemory.get(conversationId);
        int size = messages.size();
        if (size >= 2
                && messages.get(size - 1).getMessageType() == MessageType.ASSISTANT
                && messages.get(size - 2).getMessageType() == MessageType.USER) {
            List<Message> pruned = new ArrayList<>(messages.subList(0, size - 2));
            chatMemory.clear(conversationId);
            if (!pruned.isEmpty()) {
                chatMemory.add(conversationId, pruned);
            }
            log.info("[NoAnswerPruner] Pruned 'no context' turn from conversation '{}'.", conversationId);
        }
    }
}
