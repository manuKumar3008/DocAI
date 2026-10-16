package com.bsg.ai.services.extraction.services.service.pdfExtraction.components;

import com.bsg.ai.services.extraction.services.service.pdfExtraction.PageContentExtractor;
import com.bsg.ai.services.extraction.services.records.ExtractionResult;
import com.bsg.ai.services.extraction.services.records.PageExtractionContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.ai.chat.client.ChatClient;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;

/**
 * Handles fully scanned pages (no digital text, no embedded XObjects).
 * Renders the entire page as an image and calls LLM vision.
 * Only fires when soFar text is sparse AND no embedded images were found.
 * This is final backup llm call that happens if before this all extraction is failed
 */
@Slf4j
public class ScannedPageExtractor implements PageContentExtractor {

    private final int sparseThreshold;
    private final int renderDpi;

    private final ChatClient chatClient;

    public ScannedPageExtractor(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.sparseThreshold = 100;
        this.renderDpi = 300;
    }

    public ScannedPageExtractor(ChatClient chatClient, int sparseThreshold, int renderDpi) {
        this.chatClient = chatClient;
        this.sparseThreshold = sparseThreshold;
        this.renderDpi = renderDpi;
    }

    @Override
    public ExtractionResult extract(PageExtractionContext ctx, String soFar) throws Exception {
        // Only activate for sparse pages — rich digital text pages don't need this
        if (soFar.length() >= sparseThreshold) return ExtractionResult.empty();

        // Check if any XObject images exist — EmbeddedImageExtractor handles those
        boolean hasEmbeddedImages = hasXObjectImages(ctx.page());
        if (hasEmbeddedImages) return ExtractionResult.empty();

        log.info("[{}] page {} — sparse + no XObjects, rendering full page for LLM",
                name(), ctx.pageNumber());

        BufferedImage rendered = new PDFRenderer(ctx.document())
                .renderImageWithDPI(ctx.pageIndex(), renderDpi);

        String text = EmbeddedImageExtractor.callLlm(rendered, chatClient);
        if (text.contains("[NO TEXT IN IMAGE]")) return ExtractionResult.empty();

        return ExtractionResult.builder()
                .text(text)
                .metadata(Map.of("is_scanned", true, "has_images", true))
                .build();
    }

    private boolean hasXObjectImages(PDPage page) throws IOException {
        if (page.getResources() == null) return false;
        for (COSName name : page.getResources().getXObjectNames()) {
            if (page.getResources().getXObject(name) instanceof PDImageXObject) return true;
        }
        return false;
    }

    @Override
    public String name() { return "ScannedPageExtractor"; }
}
