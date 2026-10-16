package com.bsg.ai.services.extraction.services.service.pdfExtraction.components;

import com.bsg.ai.services.extraction.services.service.pdfExtraction.PageContentExtractor;
import com.bsg.ai.services.extraction.services.records.ExtractionResult;
import com.bsg.ai.services.extraction.services.records.PageExtractionContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Collects glyphs whose text matrix direction is NOT 0° or 180°.
 * These are invisible to ForkPDFLayoutTextStripper (e.g. sideways table headers).
 * No LLM.
 */
@Slf4j
public class RotatedTextExtractor implements PageContentExtractor {

    protected static final float TOLERANCE = 1f;

    @Override
    public ExtractionResult extract(PageExtractionContext ctx, String soFar) throws Exception {
        List<TextPosition> rotated = new ArrayList<>();

        new PDFTextStripper() {
            { setStartPage(ctx.pageNumber()); setEndPage(ctx.pageNumber()); }
            @Override
            protected void processTextPosition(TextPosition text) {
                float a = text.getDir();
                if (Math.abs(a) > TOLERANCE && Math.abs(a - 180f) > TOLERANCE) {
                    rotated.add(text);
                }
            }
        }.getText(ctx.document());

        if (rotated.isEmpty()) return ExtractionResult.empty();

        String text = rotated.stream()
                .collect(Collectors.groupingBy(
                        t -> Math.round(t.getDir()),
                        TreeMap::new,
                        Collectors.toList()))
                .values().stream()
                .map(group -> group.stream()
                        .sorted(Comparator.comparingDouble(TextPosition::getYDirAdj)
                                .thenComparingDouble(TextPosition::getXDirAdj))
                        .map(TextPosition::getUnicode)
                        .collect(Collectors.joining()).trim())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));

        return ExtractionResult.builder()
                .text(text)
                .metadata(Map.of("has_rotated_text", true))
                .build();
    }

    @Override
    public String name() { return "RotatedTextExtractor"; }
}
