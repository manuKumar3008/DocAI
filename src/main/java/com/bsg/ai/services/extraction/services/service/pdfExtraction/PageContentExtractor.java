package com.bsg.ai.services.extraction.services.service.pdfExtraction;

import com.bsg.ai.services.extraction.services.records.ExtractionResult;
import com.bsg.ai.services.extraction.services.records.PageExtractionContext;

/**
 * Single responsibility: extract one kind of content from one page.
 *
 * To add a new extraction concept:
 *   1. Create a class that implements this interface.
 *   2. Register it in EnhancedPdfDocumentReader — nothing else changes.
 */
public interface PageContentExtractor {
    /**
     * Extract content from the given page context.
     *
     * @param context  read-only page info (document, page, number, filename)
     * @param soFar    text already extracted by earlier extractors this page —
     *                 allows conditional logic (e.g. "only run if page is sparse")
     * @return         result containing text and optional metadata; never null
     */
    ExtractionResult extract(PageExtractionContext context, String soFar) throws Exception;

    /**
     * Human-readable name used in logs.
     */
    String name();
}
