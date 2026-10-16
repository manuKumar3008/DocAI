package com.bsg.ai.services.extraction.services.service;

import com.bsg.ai.services.extraction.qa.services.HybridElasticsearchVectorStore;
import com.bsg.ai.services.extraction.services.records.PageSegment;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.EnhancedPdfDocumentReader;
import com.bsg.ai.services.extraction.utils.SemanticDocumentChunker;
import com.bsg.ai.services.ocr.service.LlmOcrService;
import com.bsg.ai.services.ocr.service.TesseractOcrService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DocumentProcessingService {
    public static final int VECTOR_STORE_BATCH_SIZE = 50;

    private final HybridElasticsearchVectorStore vectorStore;
    private final ElasticSearchService elasticSearchService;
    private final SemanticDocumentChunker llmSemanticChunker;
    private final ChatClient chatClient;
    private final TesseractOcrService tesseractOcrService;
    private final LlmOcrService llmOcrService;
    private final ExecutorService pdfReaderExecutor;
    private final ExecutorService chunkingExecutor;
    private final int maxConcurrentBatchAdds;
    private final int bufferLimit;
    private final int readerThreads;

    public DocumentProcessingService(HybridElasticsearchVectorStore vectorStore,
                                     ElasticSearchService elasticSearchService,
                                     SemanticDocumentChunker llmSemanticChunker,
                                     ChatClient.Builder builder,
                                     TesseractOcrService tesseractOcrService,
                                     LlmOcrService llmOcrService,
                                     @Qualifier("pdfReaderExecutor") ExecutorService pdfReaderExecutor,
                                     @Qualifier("chunkingExecutor") ExecutorService chunkingExecutor,
                                     @Value("${ingestion.max-concurrent-batch-adds:2}") int maxConcurrentBatchAdds,
                                     @Value("${ingestion.buffer-limit:8000}") int bufferLimit,
                                     @Value("${ingestion.reader-threads:4}") int readerThreads) {
        this.vectorStore = vectorStore;
        this.elasticSearchService = elasticSearchService;
        this.llmSemanticChunker = llmSemanticChunker;
        this.chatClient = builder.build();
        this.tesseractOcrService = tesseractOcrService;
        this.llmOcrService = llmOcrService;
        this.pdfReaderExecutor = pdfReaderExecutor;
        this.chunkingExecutor = chunkingExecutor;
        this.maxConcurrentBatchAdds = maxConcurrentBatchAdds;
        this.bufferLimit = bufferLimit;
        this.readerThreads = readerThreads;
    }

    /**
     * @deprecated
     * This method performs naive page-by-page chunking which results in "context loss"
     * at the physical boundaries of PDF pages.
     * <p>
     * <b>Use {@code ingestDocumentWithSemanticStitching} instead</b> to ensure
     * logical continuity across page breaks.
     * </p>
     * <p>
     * Retention: This method is scheduled for removal in Future Version.
     */
    @Deprecated(forRemoval = true)
    public String ingestDocumentLegacy(MultipartFile documentFile, String documentId) {
        String internalDocumentId = documentFile.getOriginalFilename() + "_" + System.currentTimeMillis();
        try(EnhancedPdfDocumentReader pdfDocumentReader = new EnhancedPdfDocumentReader(
                documentFile.getResource(), chatClient, tesseractOcrService, llmOcrService)) {
            List<Document> pageDocuments = pdfDocumentReader.read();
            pageDocuments.forEach(document -> {
                document.getMetadata().put("internalDocId", internalDocumentId);
                document.getMetadata().put("sourceFile", documentFile.getOriginalFilename());
                document.getMetadata().put("documentId", documentId);
            });
            List<Document> buffer = new ArrayList<>();
            pageDocuments.forEach(document -> buffer.addAll(llmSemanticChunker.chunk(document)));
            vectorStore.add(buffer);
        }
        return documentId;
    }

    public String ingestDocumentWithSemanticStitching(MultipartFile documentFile, String documentId) {
        return ingestDocumentWithSemanticStitching(documentFile, documentId, false);
    }

    /**
     * CRITICAL: Processes a document using a "Semantic Bridging" strategy with 3-tier parallelism.
     *
     * <p><strong>DEDUPLICATION:</strong> The internalDocId is derived from a SHA-256 hash
     * of the file content. This allows for idempotent ingestion.</p>
     *
     * <p><strong>PARALLELISM TIERS:</strong>
     * <ol>
     *   <li>Page reading — N independent {@link EnhancedPdfDocumentReader} instances read
     *       disjoint page ranges concurrently via {@code pdfReaderExecutor}.</li>
     *   <li>Buffer assembly — sequential (stitching invariant: last segment of buffer N
     *       becomes first segment of buffer N+1).</li>
     *   <li>LLM chunking — each assembled buffer submitted to {@code chunkingExecutor}
     *       in parallel; results collected in submission order.</li>
     *   <li>Vector store add — batches fired asynchronously with bounded concurrency
     *       ({@code maxConcurrentBatchAdds}).</li>
     * </ol>
     * </p>
     *
     * @param documentFile The raw PDF file.
     * @param documentId   The external identifier for tracking.
     * @param forceReindex True if want to remove old document and add new document having same internalDocId
     * @return The original documentId upon successful ingestion.
     */
    public String ingestDocumentWithSemanticStitching(MultipartFile documentFile,
                                                      String documentId,
                                                      boolean forceReindex) {
        // calculateChecksum() consumes getInputStream(); getBytes() reads from the internal
        // byte[] independently — safe for StandardMultipartFile and disk-spooled uploads.
        String fileHash = calculateChecksum(documentFile);
        String internalDocumentId = "doc_" + fileHash;

        var existingDoc = elasticSearchService.getDocumentById(documentId);
        boolean documentExists = CollectionUtils.isNotEmpty(existingDoc);

        if (documentExists && !forceReindex) {
            log.info("Document already ingested with document id {}. Skipping...", documentId);
            return documentId;
        } else {
            removeDocumentFromStore(documentId);
        }

        if (StringUtils.isBlank(documentId)) {
            documentId = UUID.randomUUID().toString();
        }

        try {
            byte[] pdfBytes = documentFile.getBytes();
            String fileName = documentFile.getOriginalFilename();

            // Probe total page count with a single lightweight reader, then close it.
            int totalPages;
            try (EnhancedPdfDocumentReader probe = new EnhancedPdfDocumentReader(
                    new ByteArrayResource(pdfBytes) {
                        @Override public String getFilename() { return fileName; }
                    }, chatClient, tesseractOcrService, llmOcrService)) {
                totalPages = probe.getDocumentTotalPages();
            }
            log.info("Document '{}' has {} pages. Starting parallel ingestion.", fileName, totalPages);

            // ── TIER 1: Parallel page reading ────────────────────────────────────
            Map<Integer, PageSegment> pageMap =
                    readAllPagesParallel(pdfBytes, fileName, totalPages);

            List<PageSegment> orderedSegments = pageMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());

            log.info("Tier 1 complete: {}/{} non-blank pages extracted.", orderedSegments.size(), totalPages);

            if (orderedSegments.isEmpty()) {
                log.warn("No extractable text in '{}'. Skipping ingestion.", fileName);
                return documentId;
            }

            // ── TIER 2a: Sequential buffer assembly (stitching preserved) ─────────
            List<List<PageSegment>> buffers = assembleBuffers(orderedSegments);
            log.info("Tier 2a complete: {} buffers assembled.", buffers.size());

            // ── TIER 2b: Parallel LLM chunking ───────────────────────────────────
            List<Document> allChunks =
                    chunkBuffersParallel(buffers, documentId, internalDocumentId, fileName);
            log.info("Tier 2b complete: {} chunks produced.", allChunks.size());

            // ── TIER 3: Async vectorStore.add with bounded concurrency ────────────
            addToVectorStoreAsync(allChunks);
            log.info("Tier 3 complete: {} chunks ingested for '{}'.", allChunks.size(), documentId);

            return documentId;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Ingestion interrupted: " + e.getMessage(), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Ingestion failed: " + cause.getMessage(), cause);
        } catch (Exception e) {
            throw new RuntimeException("Ingestion failed: " + e.getMessage(), e);
        }
    }

    // ── Tier 1 ───────────────────────────────────────────────────────────────────

    /**
     * Reads all pages in parallel using {@code pdfReaderExecutor}.
     * Each thread gets its own {@link EnhancedPdfDocumentReader} (own {@code PDDocument})
     * constructed from the same {@code byte[]} — fully thread-safe.
     * All readers are closed in a {@code finally} block regardless of outcome.
     */
    private Map<Integer, PageSegment> readAllPagesParallel(byte[] pdfBytes,
                                                            String fileName,
                                                            int totalPages)
            throws InterruptedException, ExecutionException, TimeoutException {

        int readerCount = Math.min(readerThreads, totalPages);
        int pagesPerReader = (totalPages + readerCount - 1) / readerCount;

        List<EnhancedPdfDocumentReader> readers = new ArrayList<>();
        List<Future<Map<Integer, PageSegment>>> futures = new ArrayList<>();

        try {
            for (int t = 0; t < readerCount; t++) {
                int startPage = t * pagesPerReader + 1;
                int endPage = Math.min(startPage + pagesPerReader - 1, totalPages);

                EnhancedPdfDocumentReader reader = new EnhancedPdfDocumentReader(
                        new ByteArrayResource(pdfBytes) {
                            @Override public String getFilename() { return fileName; }
                        }, chatClient, tesseractOcrService, llmOcrService);
                readers.add(reader);

                final int from = startPage;
                final int to = endPage;
                final EnhancedPdfDocumentReader r = reader;

                futures.add(pdfReaderExecutor.submit(() -> {
                    Map<Integer, PageSegment> pageMap = new LinkedHashMap<>();
                    for (int p = from; p <= to; p++) {
                        Document doc = r.readPage(p);
                        if (doc != null && StringUtils.isNotBlank(doc.getText())) {
                            pageMap.put(p, new PageSegment(doc.getText(), p));
                        }
                    }
                    return pageMap;
                }));
            }

            Map<Integer, PageSegment> combined = new HashMap<>();
            for (Future<Map<Integer, PageSegment>> future : futures) {
                combined.putAll(future.get(10, TimeUnit.MINUTES));
            }
            return combined;

        } finally {
            for (EnhancedPdfDocumentReader reader : readers) {
                try { reader.close(); } catch (Exception e) {
                    log.warn("Failed to close PDF reader: {}", e.getMessage());
                }
            }
        }
    }

    // ── Tier 2a ──────────────────────────────────────────────────────────────────

    /**
     * Walks {@code orderedSegments} sequentially, accumulating into buffers of at most
     * {@code bufferLimit} characters. The last segment of each buffer is carried forward
     * as the first segment of the next buffer to preserve cross-buffer semantic continuity.
     */
    private List<List<PageSegment>> assembleBuffers(List<PageSegment> orderedSegments) {
        List<List<PageSegment>> buffers = new ArrayList<>();
        List<PageSegment> current = new ArrayList<>();

        for (PageSegment seg : orderedSegments) {
            current.add(seg);
            int size = current.stream().mapToInt(p -> p.text().length()).sum();

            if (size >= bufferLimit) {
                buffers.add(List.copyOf(current));

                // Keep last segment for semantic continuity into the next buffer.
                PageSegment last = current.get(current.size() - 1);
                current = new ArrayList<>(List.of(last));
            }
        }

        if (!current.isEmpty()) {
            buffers.add(List.copyOf(current));
        }
        return buffers;
    }

    // ── Tier 2b ──────────────────────────────────────────────────────────────────

    /**
     * Submits each buffer's LLM chunking to {@code chunkingExecutor} in parallel.
     * Futures are collected in submission order so chunk ordering matches document order.
     * Buffer 0 has no carry-over segment; every subsequent buffer has 1 overlap segment at index 0
     * that was already fully emitted by the previous buffer — {@code processBuffer} skips chunks
     * that fall entirely within that overlap to prevent duplicate ingestion.
     */
    private List<Document> chunkBuffersParallel(List<List<PageSegment>> buffers,
                                                 String documentId,
                                                 String internalDocumentId,
                                                 String fileName)
            throws InterruptedException, ExecutionException, TimeoutException {

        List<Future<List<Document>>> futures = new ArrayList<>();

        for (int i = 0; i < buffers.size(); i++) {
            final List<PageSegment> buffer = buffers.get(i);
            // First buffer has no carry-over; every other buffer starts with 1 overlap segment.
            final int overlapSegmentCount = (i == 0) ? 0 : 1;
            futures.add(chunkingExecutor.submit(() ->
                    processBuffer(buffer, documentId, internalDocumentId, fileName, overlapSegmentCount)));
        }

        List<Document> allChunks = new ArrayList<>();
        for (Future<List<Document>> future : futures) {
            allChunks.addAll(future.get(10, TimeUnit.MINUTES));
        }
        return allChunks;
    }

    // ── Tier 3 ───────────────────────────────────────────────────────────────────

    /**
     * Fires {@code vectorStore.add()} asynchronously with at most {@code maxConcurrentBatchAdds}
     * in-flight at once. Uses an {@link ArrayDeque} as a sliding window of futures;
     * drains completed entries before adding new ones and joins all at the end.
     */
    private void addToVectorStoreAsync(List<Document> allChunks) {
        ArrayDeque<CompletableFuture<Void>> inFlight = new ArrayDeque<>();

        for (int i = 0; i < allChunks.size(); i += VECTOR_STORE_BATCH_SIZE) {
            List<Document> batch = List.copyOf(
                    allChunks.subList(i, Math.min(i + VECTOR_STORE_BATCH_SIZE, allChunks.size())));

            drainCompleted(inFlight);

            if (inFlight.size() >= maxConcurrentBatchAdds) {
                try {
                    Objects.requireNonNull(inFlight.poll()).get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted waiting for batch add", e);
                } catch (ExecutionException e) {
                    throw new RuntimeException("vectorStore.add() failed: " + e.getCause().getMessage(), e.getCause());
                }
            }

            inFlight.add(CompletableFuture.runAsync(() -> vectorStore.add(batch), chunkingExecutor));
        }

        for (CompletableFuture<Void> f : inFlight) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted flushing final batch adds", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("vectorStore.add() failed: " + e.getCause().getMessage(), e.getCause());
            }
        }
    }

    private void drainCompleted(ArrayDeque<CompletableFuture<Void>> inFlight) {
        inFlight.removeIf(f -> {
            if (!f.isDone()) return false;
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted draining completed batch adds", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("vectorStore.add() failed: " + e.getCause().getMessage(), e.getCause());
            }
            return true;
        });
    }

    // ── Shared helper ─────────────────────────────────────────────────────────────

    /**
     * Chunks a buffer of page segments via the LLM semantic chunker and attaches metadata.
     * Thread-safe: all state is local.
     *
     * <p><strong>Page attribution strategy — Unicode boundary markers:</strong><br>
     * A {@code ⁠P{N}⁠} marker is injected on its own line before each page segment's
     * text. {@code ⁠} (WORD JOINER) survives {@link SemanticDocumentChunker#clean} because
     * Java's {@code trim()} only strips chars {@code <=  } and none of clean()'s regexes
     * match it. After chunking, each chunk's text is scanned for these markers to determine
     * exact page attribution — no {@code indexOf} on transformed text is required.</p>
     *
     * <p><strong>Overlap deduplication:</strong> The first {@code overlapSegmentCount} segments
     * are carry-over from the previous buffer. A chunk is dropped if every page it touches
     * belongs to the overlap set (i.e. its content was already emitted). Bridge chunks that
     * straddle the overlap boundary are kept and attributed to both the carry-over page and
     * the new page.</p>
     */
    private List<Document> processBuffer(List<PageSegment> segments,
                                          String documentId,
                                          String internalDocumentId,
                                          String fileName,
                                          int overlapSegmentCount) {

        // Inject ⁠P{N}⁠ on its own line before each page's text.
        // Placing the marker on a separate line keeps it away from table-detection
        // (which looks for lines starting with '|') and from ALL-CAPS header detection.
        StringBuilder fullText = new StringBuilder();
        for (PageSegment seg : segments) {
            // Sanitize: remove any pre-existing WORD JOINER chars to avoid marker collisions.
            String segText = seg.text().replace("⁠", "");
            fullText.append(pageMarker(seg.pageNumber())).append('\n')
                    .append(segText).append('\n');
        }

        List<Document> chunks = llmSemanticChunker.chunk(new Document(fullText.toString()));

        // Pages belonging to the carry-over (overlap) segment.
        Set<Integer> overlapPages = segments.subList(0, overlapSegmentCount).stream()
                .map(PageSegment::pageNumber)
                .collect(Collectors.toSet());

        List<Document> result = new ArrayList<>();
        // Tracks which page is "active" between chunks. Updated to the last marker page
        // seen so that a chunk with no leading marker knows which page it continues from.
        int activePage = segments.get(0).pageNumber();

        for (Document chunk : chunks) {
            Set<Integer> markerPages = extractMarkerPages(chunk.getText());
            String cleanText = stripMarkers(chunk.getText());

            // Advance the page cursor even for marker-only/empty chunks so subsequent
            // real chunks inherit the correct activePage.
            if (!markerPages.isEmpty()) {
                activePage = Collections.max(markerPages);
            }

            // A lone ⁠P{N}⁠ line becomes a tiny atomic-section chunk that is
            // entirely blank after marker removal — discard it.
            if (cleanText.isBlank()) continue;

            // Determine which page this chunk starts on:
            //   · Starts with ⁠ → its first marker IS the start page.
            //   · Otherwise → continues mid-page from wherever the previous chunk ended.
            Integer firstMarker = extractFirstMarkerPage(chunk.getText());
            int chunkStartPage = (firstMarker != null && chunk.getText().charAt(0) == '⁠')
                    ? firstMarker
                    : activePage;

            Set<Integer> allPages = new HashSet<>(markerPages);
            allPages.add(chunkStartPage);

            // Drop chunks that fall entirely within the carry-over overlap region.
            if (!overlapPages.isEmpty() && overlapPages.containsAll(allPages)) continue;

            Map<String, Object> meta = new HashMap<>(chunk.getMetadata());
            meta.put("documentId",    documentId);
            meta.put("sourceFile",    fileName);
            meta.put("internalDocId", internalDocumentId);
            meta.put("pages",         new HashSet<>(allPages));
            meta.put("startPage",     Collections.min(allPages));
            meta.put("endPage",       Collections.max(allPages));
            result.add(new Document(cleanText, meta));
        }

        return result;
    }

    // ── Page-marker helpers ───────────────────────────────────────────────────────
    // ⁠ = WORD JOINER — invisible, not whitespace, survives String.trim() since
    // trim() only strips chars <=   and ⁠ has value 8288.
    // Reason for this specific character is Java's trim() only strips chars <= '\u0020'.
    // U+2060 has value 8288 — it is invisible, not whitespace, and survives every step in SemanticDocumentChunker.clean().

    private static final String PAGE_MARKER_PREFIX = "⁠P";
    private static final char   PAGE_MARKER_END    = '⁠';

    private static String pageMarker(int pageNumber) {
        return PAGE_MARKER_PREFIX + pageNumber + PAGE_MARKER_END;
    }

    /** Returns page numbers of every {@code ⁠P{N}⁠} marker found in {@code text}. */
    private static Set<Integer> extractMarkerPages(String text) {
        boolean startsWithPrefix = text.startsWith(PAGE_MARKER_PREFIX);
        boolean endsWithSuffix   = text.endsWith(String.valueOf(PAGE_MARKER_END));

        Set<Integer> pages = new HashSet<>();
        int i = 0;
        while ((i = text.indexOf(PAGE_MARKER_PREFIX, i)) >= 0) {
            int start = i + PAGE_MARKER_PREFIX.length();
            int end   = text.indexOf(PAGE_MARKER_END, start);
            if (end > start) {
                try { pages.add(Integer.parseInt(text.substring(start, end))); }
                catch (NumberFormatException ignored) {}
                i = end + 1;
            } else {
                i = start;
            }
        }

        if (!pages.isEmpty()) {
            if (!startsWithPrefix) pages.add(Collections.min(pages) - 1);
            // if (!endsWithSuffix)   pages.add(Collections.max(pages) + 1);
        }

        return pages;
    }

    /** Returns the page number of the first {@code ⁠P{N}⁠} marker, or {@code null}. */
    private static Integer extractFirstMarkerPage(String text) {
        int i = text.indexOf(PAGE_MARKER_PREFIX);
        if (i < 0) return null;
        int start = i + PAGE_MARKER_PREFIX.length();
        int end   = text.indexOf(PAGE_MARKER_END, start);
        if (end <= start) return null;
        try { return Integer.parseInt(text.substring(start, end)); }
        catch (NumberFormatException ignored) { return null; }
    }

    /** Strips all {@code ⁠P{N}⁠} markers and trims surrounding whitespace. */
    private static String stripMarkers(String text) {
        return text.replaceAll("⁠P\\d+⁠", "").strip();
    }

    private String calculateChecksum(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return org.springframework.util.DigestUtils.md5DigestAsHex(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to calculate file checksum without hitting memory limits", e);
        }
    }

    /**
     * Deletes every chunk that belongs to documentId from the store.
     * Uses {@link FilterExpressionBuilder} to build a typed filter expression so that
     * the call is not tied to any specific query-string syntax.
     */
    public void removeDocumentFromStore(String documentId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression byDocumentId = b.eq("documentId", documentId).build();
        vectorStore.delete(byDocumentId);
        log.info("Deleted all chunks for document id '{}' from ES.", documentId);
    }
}
