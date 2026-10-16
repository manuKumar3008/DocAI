package com.bsg.ai.services.util;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Reactor operator that groups raw LLM streaming tokens into coherent chunks before
 * sending them to the client.  Raw tokens (typically 1-4 chars each) are accumulated
 * and flushed only at natural language boundaries, reducing the number of SSE frames
 * while keeping perceived latency low.
 *
 * <h2>Flush boundaries (in priority order)</h2>
 * <ol>
 *   <li><b>Markdown code-fence close</b> — when a {@code ```} toggles out of a code
 *       block, the entire block is flushed as one chunk.</li>
 *   <li><b>Newline</b> — {@code \n} in any token flushes the current buffer.</li>
 *   <li><b>Sentence terminator</b> — {@code .}, {@code ?}, or {@code !} at the end of
 *       the accumulated buffer, or immediately followed by a space.</li>
 *   <li><b>Word-count cap</b> — safety valve; prevents unbounded accumulation when no
 *       other boundary fires.  The cap is 3× larger inside code blocks to avoid
 *       splitting code mid-statement.</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   flux.transform(new SmartStreamBuffer())
 *   flux.transform(new SmartStreamBuffer(30))   // custom word cap
 * }</pre>
 */
public final class SmartStreamBuffer implements Function<Flux<String>, Flux<String>> {

    private static final int DEFAULT_MAX_WORDS = 40;
    private static final String CODE_FENCE = "```";

    private final int maxWords;

    public SmartStreamBuffer() {
        this(DEFAULT_MAX_WORDS);
    }

    public SmartStreamBuffer(int maxWords) {
        if (maxWords < 1) throw new IllegalArgumentException("maxWords must be >= 1");
        this.maxWords = maxWords;
    }

    @Override
    public Flux<String> apply(Flux<String> source) {
        // One State per apply() call — safe because transform(new SmartStreamBuffer())
        // creates a fresh instance per usage site.  Mono.defer on the drain ensures it
        // is evaluated lazily after the main flux completes, not at assembly time.
        //
        // handle(BiConsumer<String, SynchronousSink<String>>) keeps the whole chain
        // Flux<String> — no intermediate Flux<State> that would cause type erasure to Object.
        State state = new State(maxWords);
        return source
                .<String>handle((token, sink) -> {
                    state.accept(token);
                    if (state.flushPending) sink.next(state.flush());
                })
                .concatWith(Mono.defer(() -> {
                    String remaining = state.drain();
                    return remaining.isBlank() ? Mono.empty() : Mono.just(remaining);
                }));
    }

    // -------------------------------------------------------------------------

    private static final class State {

        private final int maxWords;
        private final StringBuilder buf = new StringBuilder();
        private int wordCount = 0;
        private boolean inCodeBlock = false;
        boolean flushPending = false;

        State(int maxWords) {
            this.maxWords = maxWords;
        }

        void accept(String token) {
            boolean wasInCodeBlock = inCodeBlock;

            // Track code-fence toggles before appending so we know the new block state.
            int idx = 0;
            while ((idx = token.indexOf(CODE_FENCE, idx)) >= 0) {
                inCodeBlock = !inCodeBlock;
                idx += CODE_FENCE.length();
            }

            buf.append(token);

            // Approximate word count via whitespace characters in the token.
            for (int i = 0; i < token.length(); i++) {
                if (Character.isWhitespace(token.charAt(i))) wordCount++;
            }

            if (!inCodeBlock && wasInCodeBlock) {
                // Code block just closed → flush the whole block.
                flushPending = true;
            } else if (inCodeBlock) {
                // Inside a code block: only the safety valve applies (wider limit).
                flushPending = wordCount >= maxWords * 3;
            } else {
                flushPending = isBoundary();
            }
        }

        String flush() {
            String out = buf.toString();
            buf.setLength(0);
            wordCount = 0;
            flushPending = false;
            return out;
        }

        /** Called once when the upstream completes; returns any leftover content. */
        String drain() {
            String out = buf.toString().stripTrailing();
            buf.setLength(0);
            return out;
        }

        private boolean isBoundary() {
            if (buf.isEmpty()) return false;

            if (wordCount >= maxWords) return true;

            int len = buf.length();
            char last = buf.charAt(len - 1);

            // Newline
            if (last == '\n') return true;

            // Sentence terminator at end of buffer
            if (last == '.' || last == '?' || last == '!') return true;

            // Sentence terminator followed by a space (e.g. ". N")
            if (len >= 2 && (last == ' ' || last == '\r')) {
                char prev = buf.charAt(len - 2);
                return prev == '.' || prev == '?' || prev == '!';
            }

            return false;
        }
    }
}
