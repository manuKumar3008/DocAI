package com.bsg.ai.services.extraction.services.service.pdfExtraction.table.strippers;

import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TextChunkCollector extends PDFTextStripper {
    //-----------------------
    public record TextChunk(String text, float left, float right, float top, float bottom){}
    //------------------------

    private final List<TextChunk> chunks = new ArrayList<>();

    public TextChunkCollector() throws IOException {
        setSortByPosition(true);
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        float left = Float.MAX_VALUE;
        float right = Float.NEGATIVE_INFINITY;
        float top = Float.MAX_VALUE;
        float bottom = Float.NEGATIVE_INFINITY;

        for (TextPosition tp : positions) {
            if (tp == null) {
                continue;
            }
            float x = tp.getXDirAdj();
            float y = tp.getYDirAdj();
            float w = tp.getWidthDirAdj();
            float h = tp.getHeightDir();
            left = Math.min(left, x);
            right = Math.max(right, x + w);
            top = Math.min(top, y);
            bottom = Math.max(bottom, y + h);
        }

        if (left != Float.MAX_VALUE) {
            chunks.add(new TextChunk(trimmed, left, right, top, bottom));
        }
    }

    public List<TextChunk> getChunks() {
        return chunks;
    }
}
