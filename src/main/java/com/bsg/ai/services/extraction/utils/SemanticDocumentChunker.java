package com.bsg.ai.services.extraction.utils;

import com.bsg.ai.services.extraction.services.records.Section;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SemanticDocumentChunker
 *
 * Production-grade chunker for RAG / QA systems that handles:
 *  - Multi-page PDF documents (page-stream buffer to heal cross-page sentence breaks)
 *  - Section-aware chunking (ALL-CAPS headers map to policy sections)
 *  - Paragraph-first strategy (small sections kept atomic, large sections split semantically)
 *  - Smoothed cosine similarity (rolling window prevents over-cutting on noisy sentences)
 *  - Inline merge decision (at cut time, checks if next segment is close enough to absorb)
 *  - Sentence overlap between chunks (ensures cross-boundary queries retrieve full context)
 *  - Metadata tagging (section title, chunk index, page range per chunk)
 */
@Component
public class SemanticDocumentChunker {

    private final EmbeddingModel embeddingModel;

    // ─── Tuning Parameters ────────────────────────────────────────────────────

    /**
     * Similarity below this triggers a chunk boundary (0.0-1.0).
     * Lower = fewer cuts = larger chunks.
     */
    private final double similarityThreshold = 0.75;

    /**
     * At a potential cut point, if similarity to the next segment is above
     * this value AND merging fits within maxChunkCharacters, the cut is
     * suppressed and accumulation continues inline.
     *
     * Should be slightly lower than similarityThreshold so we do not
     * immediately re-split what we just decided to keep together.
     * Tune: raise to merge more aggressively, lower to merge less.
     */
    private final double mergeThreshold = 0.70;

    /** Rolling window size for similarity smoothing (2-3 recommended) */
    private final int smoothingWindowSize = 2;

    /** Minimum sentences before a cut is allowed */
    private final int minSentencesPerChunk = 3;

    /** Hard character limit per chunk — merge suppression never overrides this */
    private final int maxChunkCharacters = 2000;

    /** Sections smaller than this are kept as a single atomic chunk */
    private final int minChunkCharacters = 300;

    /** Number of sentences carried forward as overlap into next chunk */
    @Getter
    private final int overlapSentences = 1;

    /** Minimum buffer size (chars) before chunking is triggered in stream mode */
    private final int minBufferCharacters = 3000;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public SemanticDocumentChunker(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Chunk a single Document (already contains full text).
     * Use this when text has been fully assembled before calling the chunker.
     */
    public List<Document> chunk(Document document) {
        return chunkByParagraphsAndSections(document);
    }

    public List<Document> mergeAndChunk(Document doc1, Document doc2) {
        return mergeSemanticChunks(List.of(doc1, doc2));
    }

    // =========================================================================
    // CORE CHUNKING — SECTION-AWARE
    // =========================================================================

    /**
     * Main chunking strategy — now table-aware.
     *
     * TABLE segments are kept atomic (never split) and get the last line of the
     * preceding segment and the first line of the following segment prepended /
     * appended as context so the retriever understands what the table is about.
     */
    private List<Document> chunkByParagraphsAndSections(Document document) {
        if (document == null || StringUtils.isBlank(document.getText())) {
            return new ArrayList<>();
        }
        // Use raw text here — clean() must NOT be called on table lines
        List<Segment>  segments  = detectSectionsAndTables(document.getText());
        List<Document> allChunks = new ArrayList<>();

        for (int idx = 0; idx < segments.size(); idx++) {
            Segment seg = segments.get(idx);

            if ("TABLE".equals(seg.type())) {
                // ── Context anchor: grab one line before and after ────────────
                String preContext  = extractTailLine(segments, idx - 1);
                String postContext = extractHeadLine(segments, idx + 1);

                String tableChunkText = buildTableChunkText(preContext, seg.rawContent(), postContext);

                Map<String, Object> meta = new HashMap<>(document.getMetadata());
                meta.put("section",   "TABLE");
                meta.put("chunkType", "atomic_table");
                allChunks.add(new Document(tableChunkText, meta));

            } else {
                // ── Normal section chunking (unchanged logic) ─────────────────
                String cleanedContent = clean(seg.content());
                int    len            = cleanedContent.length();
                Segment cleanedSeg    = Segment.section(seg.title(), cleanedContent);

                if (len <= maxChunkCharacters) {
                    allChunks.add(buildSectionChunk(document, toSection(cleanedSeg), 0));
                } else {
                    Document sectionDoc = new Document(cleanedContent, document.getMetadata());
                    allChunks.addAll(chunkSectionWithInlineMerge(sectionDoc, cleanedSeg.title()));
                }
            }
        }

        return allChunks;
    }

// ── Helpers ────────────────────────────────────────────────────────────────

    /** Last non-empty line of the segment immediately before the table. */
    private String extractTailLine(List<Segment> segments, int segIdx) {
        if (segIdx < 0) return "";
        String content = segments.get(segIdx).content().trim();
        String[] lines = content.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].trim().isEmpty()) return lines[i].trim();
        }
        return "";
    }

    /** First non-empty line of the segment immediately after the table. */
    private String extractHeadLine(List<Segment> segments, int segIdx) {
        if (segIdx >= segments.size()) return "";
        String content = segments.get(segIdx).content().trim();
        for (String line : content.split("\n")) {
            if (!line.trim().isEmpty()) return line.trim();
        }
        return "";
    }

    /** Assembles the final table chunk: [pre]\n[table]\n[post] */
    private String buildTableChunkText(String pre, String table, String post) {
        StringBuilder sb = new StringBuilder();
        if (!pre.isEmpty())  sb.append(pre).append("\n");
        sb.append(table);
        if (!post.isEmpty()) sb.append("\n").append(post);
        return sb.toString();
    }

    /** Bridges the new Segment record back to the old Section record for unchanged helpers. */
    private Section toSection(Segment seg) {
        return new Section(seg.title(), seg.content());
    }

    // =========================================================================
    // SECTION DETECTION
    // =========================================================================

    /**
     * Splits text into ordered segments: SECTION blocks and TABLE blocks.
     *
     * Tables are identified as contiguous groups of lines where every non-empty
     * line starts with '|' (standard Markdown pipe tables). Each table is
     * extracted as an atomic TABLE segment so it is never split mid-row.
     *
     * Within non-table regions, ALL-CAPS lines are treated as section headers
     * exactly as before.
     */
private List<Segment> detectSectionsAndTables(String rawText) {
    String[] lines = rawText.split("\n", -1);
    List<Segment> out = new ArrayList<>();

    boolean[] isTableLine = new boolean[lines.length];
    for (int i = 0; i < lines.length; i++) {
        isTableLine[i] = lines[i].trim().startsWith("|");
    }

    int i = 0;
    while (i < lines.length) {
        if (isTableLine[i]) {
            int start = i;
            while (i < lines.length && isTableLine[i]) i++;

            // Guard: require at least 2 consecutive pipe-lines AND consistent
            // column count to trust this as a genuine table, not stray "|" artifacts.
            int rowCount = i - start;
            boolean looksLikeRealTable = rowCount >= 2 && hasConsistentColumnCount(lines, start, i);

            if (looksLikeRealTable) {
                String tableText = Arrays.stream(lines, start, i).collect(Collectors.joining("\n"));
                out.add(Segment.table(tableText));
            } else {
                // Treat as ordinary text instead
                String block = Arrays.stream(lines, start, i).collect(Collectors.joining("\n")).trim();
                if (!block.isEmpty()) {
                    out.addAll(detectSectionsInBlock(block));
                }
            }
        }  else {
                // Collect non-table text block
                int start = i;
                while (i < lines.length && !isTableLine[i]) i++;
                String block = Arrays.stream(lines, start, i)
                        .collect(Collectors.joining("\n"))
                        .trim();
                if (!block.isEmpty()) {
                    // Apply existing section-header logic to this text block
                    out.addAll(detectSectionsInBlock(block));
                }
            }
        }
        return out;
    }
    private boolean hasConsistentColumnCount(String[] lines, int start, int end) {
    int firstCount = -1;
    for (int i = start; i < end; i++) {
        int cols = (int) lines[i].chars().filter(c -> c == '|').count();
        if (firstCount == -1) firstCount = cols;
        else if (Math.abs(cols - firstCount) > 1) return false;
        }
    return firstCount >= 2;
}
    /**
     * Runs the original ALL-CAPS header detection on a pure-text block
     * (no tables present). Extracted from the old detectSections() so it
     * can be called per-block after tables are separated out.
     */
    private List<Segment> detectSectionsInBlock(String text) {
        List<Segment> sections  = new ArrayList<>();
        Pattern headerPattern   = Pattern.compile("(?m)^([A-Z][A-Z\\s]{3,})$");
        Matcher matcher         = headerPattern.matcher(text);

        List<Integer> positions = new ArrayList<>();
        List<String>  titles    = new ArrayList<>();

        while (matcher.find()) {
            positions.add(matcher.start());
            titles.add(matcher.group().trim());
        }

        if (positions.isEmpty()) {
            sections.add(Segment.section("DOCUMENT", text));
            return sections;
        }

        if (positions.get(0) > 0) {
            String preamble = text.substring(0, positions.get(0)).trim();
            if (!preamble.isEmpty()) {
                sections.add(Segment.section("PREAMBLE", preamble));
            }
        }

        for (int i = 0; i < positions.size(); i++) {
            int    start   = positions.get(i);
            int    end     = (i + 1 < positions.size()) ? positions.get(i + 1) : text.length();
            String content = text.substring(start, end).trim();
            sections.add(Segment.section(titles.get(i), content));
        }

        return sections;
    }

    // =========================================================================
    // SEMANTIC CHUNKING WITH INLINE MERGE
    // =========================================================================

    /**
     * Splits a large section into semantic chunks with inline merge decisions.
     *
     * At every potential cut point the decision tree is:
     *
     *   STEP 1 — SHOULD CUT?
     *     (similarity dropped below threshold AND min size met) OR hard char limit hit
     *     If no -> continue accumulating, no further checks needed.
     *
     *   STEP 2 — HARD CUT? (char limit overflow)
     *     If overCharLimit is the reason -> always cut immediately.
     *     Merge suppression does NOT apply to hard cuts.
     *     This prevents runaway chunk accumulation.
     *
     *   STEP 3 — INLINE MERGE CHECK (soft cut only)
     *     Even though similarity dropped, peek at the next window:
     *       a) Is sim to next sentence >= mergeThreshold?  (semantically close)
     *       b) Would current + remaining text fit in maxChunkCharacters?  (size safe)
     *     If BOTH true -> suppress the cut, keep accumulating.
     *     This decision fires at cut time — no second pass, no re-embedding.
     *
     *   STEP 4 — CONFIRMED CUT
     *     Emit current chunk, carry overlap sentences into next chunk.
     *
     * suffixLen[] is precomputed so the merge size check is O(1) per sentence.
     */
    private List<Document> chunkSectionWithInlineMerge(Document document, String sectionTitle) {
        if (document == null || StringUtils.isBlank(document.getText())) {
            return new ArrayList<>();
        }
        List<String> sentences = splitIntoSentences(document.getText());

        if (sentences.isEmpty()) return List.of(document);
        if (sentences.size() == 1) return List.of(document);

        List<float[]> embeddings = embedSentences(sentences);
        double[]      smoothed   = computeSmoothedSimilarities(embeddings);

        // Precompute suffix character lengths.
        // suffixLen[i] = total chars from sentence[i] to end of section.
        // Lets us check "would merging fit?" in O(1) without a forward scan.
        int[] suffixLen = new int[sentences.size()];
        suffixLen[sentences.size() - 1] = sentences.get(sentences.size() - 1).length();
        for (int i = sentences.size() - 2; i >= 0; i--) {
            suffixLen[i] = suffixLen[i + 1] + sentences.get(i).length();
        }

        List<Document> chunks     = new ArrayList<>();
        List<String>   current    = new ArrayList<>();
        int            currentLen = 0;
        int            chunkIndex = 0;

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            current.add(sentence);
            currentLen += sentence.length();

            boolean isLast = (i == sentences.size() - 1);

            if (isLast) {
                // Always flush remaining sentences as the final chunk
                chunks.add(buildChunkWithMeta(document, current, sectionTitle, chunkIndex++));
                break;
            }

            double  sim            = smoothed[i];
            boolean belowThreshold = sim < similarityThreshold;
            boolean overCharLimit  = currentLen >= maxChunkCharacters;
            boolean meetsMinSize   = current.size() >= minSentencesPerChunk;

            boolean shouldCut = (belowThreshold && meetsMinSize) || overCharLimit;

            if (shouldCut) {

                if (!overCharLimit) {
                    // ── Inline merge check (soft cut only) ────────────────────
                    //
                    // We are at a similarity drop boundary. Before cutting, ask:
                    //   Is the NEXT segment semantically close enough to absorb?
                    //   Would the combined text still fit within the char limit?
                    //
                    // nextIsClose: smoothed sim at this boundary >= mergeThreshold
                    // mergedFits:  currentLen + all remaining chars <= maxChunkCharacters
                    //
                    // If both conditions hold -> suppress cut, keep accumulating.
                    // The merge happens right here, at cut decision time.

                    boolean nextIsClose = sim >= mergeThreshold;
                    boolean mergedFits  = (currentLen + suffixLen[i + 1]) <= maxChunkCharacters;

                    if (nextIsClose && mergedFits) {
                        continue; // suppress cut — semantically close and fits
                    }
                }

                // ── Confirmed cut ─────────────────────────────────────────────
                // Hard char overflow, or merge conditions were not satisfied.
                chunks.add(buildChunkWithMeta(document, current, sectionTitle, chunkIndex++));

                // Carry last N sentences forward as overlap so cross-boundary
                // queries can still retrieve context that spans the cut point.
                current    = new ArrayList<>(
                        current.subList(Math.max(0, current.size() - overlapSentences), current.size())
                );
                currentLen = current.stream().mapToInt(String::length).sum();
            }
        }

        return chunks;
    }

    // =========================================================================
    // SIMILARITY COMPUTATION — SMOOTHED
    // =========================================================================

    /**
     * Computes smoothed cosine similarity for each adjacent sentence pair.
     *
     * Instead of reacting to a single noisy similarity drop, averages raw
     * similarities over a rolling window of [i - windowSize, i + windowSize].
     * This prevents one semantically odd sentence from causing a premature cut.
     */
    private double[] computeSmoothedSimilarities(List<float[]> embeddings) {
        int      n        = embeddings.size() - 1;
        double[] raw      = new double[n];
        double[] smoothed = new double[n];

        for (int i = 0; i < n; i++) {
            raw[i] = cosineSimilarity(embeddings.get(i), embeddings.get(i + 1));
        }

        for (int i = 0; i < n; i++) {
            int    start = Math.max(0, i - smoothingWindowSize);
            int    end   = Math.min(n - 1, i + smoothingWindowSize);
            double sum   = 0;
            int    count = 0;
            for (int j = start; j <= end; j++) {
                sum += raw[j];
                count++;
            }
            smoothed[i] = sum / count;
        }

        return smoothed;
    }

    // =========================================================================
    // TEXT PREPROCESSING
    // =========================================================================

    /**
     * Cleans raw PDF-extracted text before chunking.
     *
     * Fixes in order:
     *  1.  Normalize line endings
     *  2.  Collapse excessive newlines to paragraph breaks
     *  3.  Heal hyphenated line breaks (word-\nbreak -> wordbreak)
     *  4.  Merge lettered/numbered list items into parent sentence
     *  5.  Remove mid-sentence line breaks
     *  6.  Collapse whitespace
     *  7.  Fix spaced-out acronyms (safe uppercase-only version)
     *  8.  Strip ALL-CAPS section headers (re-captured as metadata)
     *  9.  Re-collapse blank lines after header removal
     *  10. Trim lines, remove empties
     *  11. Final trim
     */
    public static String clean(String text) {
        if (text == null) return "";

        return text
                // 1. Normalize line endings
                .replace("\r", "\n")

                // 2. Collapse 3+ newlines to a single paragraph break
                .replaceAll("\n{3,}", "\n\n")

                // 3. Heal end-of-line hyphenation: "configu-\nration" -> "configuration"
                .replaceAll("-\\n", "")

                // 4. Merge lettered/numbered list items into their parent paragraph.
                // "a) Rule one.\nb) Rule two." becomes one sentence block,
                // preventing each list item becoming an orphan micro-chunk.
                .replaceAll("\n\\s*[a-zA-Z0-9]\\s*[).] +", " ")

                // 5. Remove line breaks inside sentences (not paragraph breaks).
                // Negative lookbehind ensures only single \n is removed, not \n\n.
                .replaceAll("(?<!\\n)\\n(?!\\n)", " ")

                // 6. Collapse multiple spaces and tabs to one space
                .replaceAll("[ \\t]{2,}", " ")

                // 7. Fix spaced-out acronyms: "P D F" -> "PDF"
                // Safe version: only collapses consecutive single UPPERCASE letters.
                // The original (?<=\b\w) pattern destroyed short words like "a", "I".
                .replaceAll("(?<=\\b[A-Z])\\s+(?=[A-Z]\\b)", "")

                // 8. Remove ALL-CAPS section headers — they become orphan sentences
                // causing spurious cuts. Section title is preserved in metadata
                // via detectSections() so no information is lost here.
                .replaceAll("(?m)^[A-Z][A-Z\\s]{3,}$", "")

                // 9. Re-collapse blank lines left behind by header removal
                .replaceAll("\n{3,}", "\n\n")

                // 10. Trim each line, drop fully empty lines
                .lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("")

                // 11. Final trim
                .trim();
    }

    // =========================================================================
    // SENTENCE SPLITTING
    // =========================================================================

    /**
     * Splits text into sentences. Handles:
     *  - Standard sentence endings: . ! ?
     *  - Avoids splitting common abbreviations: etc. e.g. Mr. vs.
     *  - List items merged by clean() are treated as one sentence block
     */
    public List<String> splitIntoSentences(String text) {
        String[] parts = text.split(
                "(?<![A-Z])(?<![a-z]\\.[a-z])(?<=[.!?])\\s+(?=[A-Z])"
        );

        return Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // =========================================================================
    // EMBEDDING
    // =========================================================================

    /**
     * Embeds all sentences in a single batch call.
     * Spring AI's EmbeddingModel.embed(List) handles batching internally.
     */
    private List<float[]> embedSentences(List<String> sentences) {
        return embeddingModel.embed(sentences);
    }

    // =========================================================================
    // COSINE SIMILARITY
    // =========================================================================

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot   += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }

    // =========================================================================
    // PARAGRAPH BOUNDARY DETECTION (stream / page-buffer mode)
    // =========================================================================
    /**
     * Returns true if a chunk looks like a sentence fragment carried over
     * from a previous page — i.e. it starts mid-sentence (lowercase or
     * connective word) and is shorter than a full standalone thought.
     *
     * These should always be merged with their predecessor regardless of
     * similarity score or section label.
     */
    private boolean isSentenceFragment(String text) {
        if (text == null || text.isBlank()) return false;

        String trimmed = text.trim();

        // Starts with lowercase → definitely a continuation
        if (Character.isLowerCase(trimmed.charAt(0))) return true;

        // Starts with a connective/continuation word (common cross-page patterns)
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("does not") ||
                lower.startsWith("provided that") ||
                lower.startsWith("subject to") ||
                lower.startsWith("and ") ||
                lower.startsWith("or ") ||
                lower.startsWith("but ") ||
                lower.startsWith("which ") ||
                lower.startsWith("that ")) {
            return true;
        }

        // Very short chunk that ends a sentence — almost certainly a fragment
        // (e.g. "does not exceed 60 days. Maternity leave shall be...")
        return trimmed.length() < minChunkCharacters;
    }

    // =========================================================================
    // CHUNK BUILDERS
    // =========================================================================

    private Document buildSectionChunk(Document source, Section section, int index) {
        Map<String, Object> meta = new HashMap<>(source.getMetadata());
        meta.put("section",    section.title());
        meta.put("chunkIndex", index);
        meta.put("chunkType",  "atomic_section");
        return new Document(section.content(), meta);
    }

    private Document buildChunkWithMeta(Document source, List<String> sentences, String sectionTitle, int index) {
        String text = String.join(" ", sentences);
        Map<String, Object> meta = new HashMap<>(source.getMetadata());
        meta.put("section",    sectionTitle);
        meta.put("chunkIndex", index);
        meta.put("chunkType",  "semantic_split");
        return new Document(text, meta);
    }

    /**
     * Merges adjacent semantic chunks under two policies:
     *
     * ── TABLE + TABLE (new) ───────────────────────────────────────────────────
     * Adjacent table chunks are always merged unconditionally.
     * Tables extracted side-by-side from the same document region belong
     * together; size and similarity checks would incorrectly split them.
     *
     * ── TEXT + TEXT (existing, fixed) ────────────────────────────────────────
     * Merge when:
     *   a) Cosine similarity >= similarityThreshold AND combined size fits, OR
     *   b) Same section, similarity >= (threshold - 0.05), AND size fits.
     *
     * Bugs fixed vs. original:
     *   1. mergeCount now starts at 2 (not 1+1=2 via 1+1 arithmetic accident)
     *      and correctly reflects total chunks collapsed, not increments.
     *   2. mergeSimilarity now tracks the MINIMUM similarity seen across all
     *      merges (the weakest join) instead of overwriting with the last value.
     *   3. chunkType is updated to "merged" on the first merge and kept stable.
     *   4. Table chunks are never fed into computeChunkSimilarity — that would
     *      embed pipe-delimited text and produce meaningless similarity scores.
     *   5. Null-safe chunkType read via getOrDefault.
     */
    private List<Document> mergeSemanticChunks(List<Document> chunks) {

        if (chunks.isEmpty()) return chunks;

        List<Document> result = new ArrayList<>();

        Document            current     = chunks.get(0);
        String              currentText = current.getText();
        Map<String, Object> currentMeta = new HashMap<>(current.getMetadata());

        for (int i = 1; i < chunks.size(); i++) {

            Document next         = chunks.get(i);
            String   nextType     = (String) next.getMetadata().getOrDefault("chunkType", "");
            String   currentType  = (String) currentMeta.getOrDefault("chunkType", "");

            // ── PRIORITY 0: Fragment healing ──────────────────────────────────────
            // If the next chunk is a sentence fragment, it belongs to the current
            // chunk unconditionally — section label and similarity are irrelevant.
            if (isSentenceFragment(next.getText())) {
                currentText = currentText + " " + next.getText();
                applyMergeMeta(currentMeta, currentMeta.getOrDefault("chunkType", "merged").toString(), null);
                if (currentText.length() > maxChunkCharacters) {
                    List<Document> splitChunks = chunkSectionWithInlineMerge(
                                    new Document(currentText, currentMeta),
                                    currentMeta.getOrDefault("section", "UNKNOWN").toString()
                            );

                    // Add all except last
                    for (int j = 0; j < splitChunks.size() - 1; j++) {
                        result.add(splitChunks.get(j));
                    }

                    // Keep last as current accumulator
                    Document last = splitChunks.get(splitChunks.size() - 1);
                    currentText = last.getText();
                    currentMeta = new HashMap<>(last.getMetadata());
                }
            }

            boolean bothTables = "atomic_table".equals(currentType)
                    && "atomic_table".equals(nextType);

            if (bothTables) {
                // ── TABLE + TABLE: always merge, no similarity/size check ─────
                currentText = currentText + "\n" + next.getText();
                applyMergeMeta(currentMeta, "atomic_table", null);

            } else if (!"atomic_table".equals(currentType) && !"atomic_table".equals(nextType)) {
                // ── TEXT + TEXT: existing similarity + size logic (fixed) ─────
                double  similarity  = computeChunkSimilarity(currentText, next.getText());
                boolean sameSection = Objects.equals(currentMeta.get("section"), next.getMetadata().get("section"));
                boolean withinSize  = (currentText.length() + next.getText().length()) <= maxChunkCharacters;

                if (shouldMergeChunks(similarity, sameSection, withinSize)) {
                    currentText = currentText + " " + next.getText();
                    applyMergeMeta(currentMeta, "merged", similarity);

                } else {
                    // Finalize current, advance
                    result.add(new Document(currentText, currentMeta));
                    currentText = next.getText();
                    currentMeta = new HashMap<>(next.getMetadata());
                }

            } else {
                // ── TABLE + TEXT or TEXT + TABLE: never merge ─────────────────
                // Mixing structured and prose into one chunk harms both
                // embedding quality and LLM readability.
                result.add(new Document(currentText, currentMeta));
                currentText = next.getText();
                currentMeta = new HashMap<>(next.getMetadata());
            }
        }

        // Flush the last accumulated chunk
        result.add(new Document(currentText, currentMeta));
        return result;
    }

    /**
     * Updates metadata in-place when a merge occurs.
     *
     * @param meta       the current (accumulating) chunk's metadata map
     * @param chunkType  type to set — "merged" for text, "atomic_table" for tables
     * @param similarity cosine similarity of this join, or null for table merges
     *                   (table merges are unconditional so similarity is meaningless)
     *
     * mergeCount reflects total source chunks collapsed:
     *   First merge:  2 (current + next = 2 chunks became 1)
     *   Second merge: 3 (now 3 source chunks in this accumulation), etc.
     *
     * mergeSimilarityMin tracks the weakest semantic join across all merges.
     * The minimum is the most informative signal — if any join was weak,
     * the whole merged chunk may span a topic boundary.
     */
    private void applyMergeMeta(Map<String, Object> meta,
                                String chunkType,
                                Double similarity) {

        int currentCount = (int) meta.getOrDefault("mergeCount", 1);

        meta.put("chunkType",  chunkType);
        meta.put("merged",     true);
        meta.put("mergeCount", currentCount + 1); // +1 for the chunk just absorbed

        if (similarity != null) {
            double currentMin = (double) meta.getOrDefault("mergeSimilarityMin", similarity);
            meta.put("mergeSimilarityMin", Math.min(currentMin, similarity));
        }

        // Remove stale single-value similarity key if it was set by an earlier version
        meta.remove("mergeSimilarity");
    }

    private double computeChunkSimilarity(String text1, String text2) {

        List<String> s1 = splitIntoSentences(text1);
        List<String> s2 = splitIntoSentences(text2);

        if (s1.isEmpty() || s2.isEmpty()) return 0;

        String tail = joinLastSentences(s1, 3);
        String head = joinFirstSentences(s2, 3);

        List<float[]> embeddings = embeddingModel.embed(List.of(tail, head));

        return cosineSimilarity(embeddings.get(0), embeddings.get(1));
    }

    private boolean shouldMergeChunks(double similarity, boolean sameSection, boolean withinSize) {

        // Strong semantic signal
        if (similarity >= similarityThreshold && withinSize) {
            return true;
        }

        // Slightly relaxed if same section
        if (sameSection && similarity >= (similarityThreshold - 0.05) && withinSize) {
            return true;
        }

        return false;
    }

    private String joinLastSentences(List<String> sentences, int n) {
        int start = Math.max(0, sentences.size() - n);
        return String.join(" ", sentences.subList(start, sentences.size()));
    }

    private String joinFirstSentences(List<String> sentences, int n) {
        int end = Math.min(n, sentences.size());
        return String.join(" ", sentences.subList(0, end));
    }

    // =========================================================================
    // SEGMENT MODEL (replaces Section to support TABLE type)
    // =========================================================================

    /**
     * Represents a parsed segment of the document.
     * type = "SECTION" | "TABLE"
     * For TABLE segments, rawLines holds the original unmodified table lines.
     */
    private record Segment(String type, String title, String content, String rawContent) {
        static Segment section(String title, String content) {
            return new Segment("SECTION", title, content, content);
        }
        static Segment table(String raw) {
            return new Segment("TABLE", "TABLE", raw, raw);
        }
    }
}
