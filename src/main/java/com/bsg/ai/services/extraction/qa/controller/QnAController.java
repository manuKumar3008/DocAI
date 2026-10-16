package com.bsg.ai.services.extraction.qa.controller;

import com.bsg.ai.services.extraction.qa.records.QuestionRequest;
import com.bsg.ai.services.extraction.qa.services.QnAService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import org.springframework.http.ResponseEntity;
import java.util.UUID;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/document-question-answer")
@RequiredArgsConstructor
public class QnAController {

    private final QnAService qnAService;

    // -------------------------------------------------------------------------
    // Non-streaming (Mono) endpoints — full answer returned in a single SSE event
    // -------------------------------------------------------------------------

    /**
     * Collects the full LLM answer and returns it as a single Server-Sent Event.
     *
     * <p>Returns {@code Flux} (not {@code Mono}) because Spring MVC's Servlet stack routes
     * only multi-value publishers ({@code Flux}) through the SSE {@code SseEmitter} path.
     * A {@code Mono<ServerSentEvent>} would be treated as a plain single-value response,
     * fail to find a message converter, and throw {@code HttpMessageNotWritableException}.</p>
     *
     * <p>SSE event structure — single event:
     * <pre>
     * event: message
     * data: &lt;complete LLM answer&gt;
     * </pre>
     */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> ask(@RequestBody QuestionRequest questionRequest) {
        return qnAService.askAboutDocument(questionRequest.question(), questionRequest.documentIds())
                .collect(Collectors.joining())
                .map(fullAnswer -> ServerSentEvent.<String>builder()
                        .id(UUID.randomUUID().toString())
                        .event("message")
                        .data(fullAnswer)
                        .build())
                .flux();
    }

    /**
     * Same as {@link #ask} but maintains conversation history across calls using
     * {@code conversationId} as the session key. Returns the full answer in a single SSE event.
     *
     * <p>See {@link #ask} for why the return type is {@code Flux} rather than {@code Mono}.</p>
     *
     * <p>SSE event structure — single event:
     * <pre>
     * event: message
     * data: &lt;complete LLM answer&gt;
     * </pre>
     */
    @PostMapping(value = "/ask-with-history", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> askWithHistory(@RequestBody QuestionRequest questionRequest) {
        return qnAService.askWithHistory(
                        questionRequest.question(),
                        questionRequest.documentIds(),
                        questionRequest.conversationId())
                .collect(Collectors.joining())
                .map(fullAnswer -> ServerSentEvent.<String>builder()
                        .id(UUID.randomUUID().toString())
                        .event("message")
                        .data(fullAnswer)
                        .build())
                .flux();
    }

    // -------------------------------------------------------------------------
    // Streaming (Flux) endpoints — tokens emitted as they arrive from the LLM
    // -------------------------------------------------------------------------

    /**
     * Streams the LLM answer token-by-token as Server-Sent Events.
     *
     * <p>SSE event structure per chunk:
     * <pre>
     * event: message
     * data: &lt;one or more LLM tokens&gt;
     * </pre>
     * A final completion event signals the end of the stream:
     * <pre>
     * event: done
     * data: [DONE]
     * </pre>
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> askStream(@RequestBody QuestionRequest questionRequest) {
        return qnAService.askAboutDocument(questionRequest.question(), questionRequest.documentIds())
                .map(token -> ServerSentEvent.<String>builder()
                        .id(UUID.randomUUID().toString())
                        .event("message")
                        .data(token)
                        .build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .id(UUID.randomUUID().toString())
                        .event("done")
                        .data("[DONE]")
                        .build()));
    }

    /**
     * Same as {@link #askStream} but maintains conversation history across calls using
     * {@code conversationId} as the session key. Follow-up questions are automatically
     * rewritten into self-contained queries before RAG retrieval fires.
     *
     * <p>Emits the same {@code token} / {@code done} SSE event structure as {@link #askStream}.
     */
    @PostMapping(value = "/ask-with-history/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> askWithHistoryStream(@RequestBody QuestionRequest questionRequest) {
        return qnAService.askWithHistory(
                        questionRequest.question(),
                        questionRequest.documentIds(),
                        questionRequest.conversationId())
                .map(token -> ServerSentEvent.<String>builder()
                        .event("token")
                        .data(token)
                        .build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("[DONE]")
                        .build()));
    }
    @PostMapping(value = "/ask-with-history/sync", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<QnAService.QnAWithReferences> askWithHistorySync(@RequestBody QuestionRequest questionRequest) {
    return ResponseEntity.ok(qnAService.askWithHistorySync(
            questionRequest.question(),
            questionRequest.documentIds(),
            questionRequest.conversationId()));
}
}
