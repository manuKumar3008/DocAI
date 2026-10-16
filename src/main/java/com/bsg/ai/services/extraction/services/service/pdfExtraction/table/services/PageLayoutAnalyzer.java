package com.bsg.ai.services.extraction.services.service.pdfExtraction.table.services;

import com.bsg.ai.services.extraction.services.records.PageExtractionContext;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.PageLayout;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.strippers.TextChunkCollector;

import java.io.IOException;
import java.util.*;

class PageLayoutAnalyzer {

//    private final BorderedTableExtractor borderedTableExtractor;

    PageLayoutAnalyzer() {
//        this.borderedTableExtractor = borderedTableExtractor;
    }

    PageLayout identifyPageLayout(PageExtractionContext ctx) throws IOException {
        TextChunkCollector collector = new TextChunkCollector();
        collector.setStartPage(ctx.pageNumber());
        collector.setEndPage(ctx.pageNumber());
        collector.getText(ctx.document());
        List<TextChunkCollector.TextChunk> chunks = collector.getChunks();

        float pageWidth = ctx.page().getArtBox().getWidth();
        if (hasVerticalGutter(chunks, pageWidth)) {
            return PageLayout.MULTI_COLUMN;
        }

        return PageLayout.SINGLE_COLUMN;
    }

    private boolean hasVerticalGutter(List<TextChunkCollector.TextChunk> chunks, float pageWidth) {
        if (chunks.size() < 20) return false;

        // 1. Create a frequency map of 'left' coordinates
        // We round to nearest integer to group slightly misaligned text
        Map<Integer, Integer> leftAlignmentCounts = new HashMap<>();

        // Ignore headers and footers to keep the data clean
        float topMargin = 60f;
        float bottomMargin = pageWidth * 1.3f; // Simple heuristic for page height

        for (TextChunkCollector.TextChunk chunk : chunks) {
            if (chunk.top() < topMargin || chunk.bottom() > bottomMargin) continue;

            int leftX = Math.round(chunk.left());
            leftAlignmentCounts.put(leftX, leftAlignmentCounts.getOrDefault(leftX, 0) + 1);
        }

        // 2. Find "Significant" alignment peaks
        // A peak is significant if it represents at least 15% of the total lines
        int threshold = (int) (chunks.size() * 0.15);
        List<Integer> peaks = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : leftAlignmentCounts.entrySet()) {
            if (entry.getValue() > threshold) {
                peaks.add(entry.getKey());
            }
        }

        // 3. Evaluate the peaks
        // If we have two major peaks separated by at least 20% of the page width,
        // it's almost certainly multi-column.
        if (peaks.size() >= 2) {
            Collections.sort(peaks);
            for (int i = 0; i < peaks.size() - 1; i++) {
                if (peaks.get(i + 1) - peaks.get(i) > (pageWidth * 0.25)) {
                    return true;
                }
            }
        }

        return false;
    }
}
