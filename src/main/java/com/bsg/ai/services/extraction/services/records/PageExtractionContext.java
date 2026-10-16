package com.bsg.ai.services.extraction.services.records;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Immutable context passed to every PageContentExtractor.
 * Extractors read from this — they never modify it.
 */
public record PageExtractionContext(PDDocument document, PDPage page, int pageNumber, int totalPages, String fileName) {
    /** Convenience: zero-based index for PDFRenderer calls */
    public int pageIndex() {
        return pageNumber - 1;
    }
}
