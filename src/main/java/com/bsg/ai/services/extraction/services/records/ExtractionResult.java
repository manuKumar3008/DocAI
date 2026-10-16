package com.bsg.ai.services.extraction.services.records;

import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.DetectedTable;
import lombok.Builder;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * What a single extractor contributes for one page.
 * Multiple results are merged by the reader into one Document.
 * text = extracted text (can be empty)
 * metadata = extractor-specific metadata keys (can be empty)
 */
@Builder
public record ExtractionResult(String text, Map<String, Object> metadata, List<DetectedTable> tables) {
    public static ExtractionResult empty() {
        return ExtractionResult.builder()
                .text("")
                .tables(List.of())
                .metadata(Map.of())
                .build();
    }

    public boolean hasText() {
        return StringUtils.hasText(text) || CollectionUtils.isNotEmpty(tables);
    }

    public boolean hasTables() {
        return CollectionUtils.isNotEmpty(tables);
    }
}
