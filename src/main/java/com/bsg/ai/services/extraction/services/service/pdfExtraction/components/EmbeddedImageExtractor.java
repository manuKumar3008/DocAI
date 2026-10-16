package com.bsg.ai.services.extraction.services.service.pdfExtraction.components;

import com.bsg.ai.services.extraction.services.records.PositionedImage;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.PageContentExtractor;
import com.bsg.ai.services.extraction.services.records.ExtractionResult;
import com.bsg.ai.services.extraction.services.records.PageExtractionContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Why this extractor exists:
 * digital text extraction alone misses documents where meaningful content is shipped as
 * embedded raster images (stamps, signatures, scanned sections inside an otherwise digital PDF).
 * This component recovers that signal early so downstream retrieval quality does not depend on
 * a full-page OCR fallback for every mixed-layout page.
 */
@Slf4j
public class EmbeddedImageExtractor implements PageContentExtractor {

    private static final String NO_TEXT_SENTINEL = "[NO TEXT IN IMAGE]";
    private static final int DEFAULT_MIN_IMAGE_PX = 100;
    private static final int STRIP_FALLBACK_THRESHOLD = 5;
    private static final int FULL_PAGE_RENDER_DPI = 300;
    private static final int MAX_LLM_IMAGE_CANDIDATES = 8;
    /** How far apart (page-space units) two strips can be and still be considered one group. */
    private static final float STRIP_X_TOLERANCE = 5f;

    private final ChatClient chatClient;
    private final int minImagePx;

    public EmbeddedImageExtractor(ChatClient chatClient) {
        this(chatClient, DEFAULT_MIN_IMAGE_PX);
    }

    public EmbeddedImageExtractor(ChatClient chatClient, int minImagePx) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient is required");
        this.minImagePx = minImagePx > 0 ? minImagePx : DEFAULT_MIN_IMAGE_PX;
    }

    @Override
    public ExtractionResult extract(PageExtractionContext ctx, String soFar) throws Exception {
        List<PositionedImage> positioned = extractPositionedImages(ctx.page());
        if (positioned.isEmpty()) return ExtractionResult.empty();

        List<BufferedImage> candidates = new ArrayList<>();
        ClassifiedImageCandidates imageCandidates = classifyImageCandidates(positioned, ctx.page());
        if (imageCandidates.stripCandidates.size() > STRIP_FALLBACK_THRESHOLD) {
            // Why this fallback exists:
            // a page split into many narrow slices usually represents one visual block.
            // Sending each slice to the model increases cost and harms reading-order quality.
            candidates.add(renderPageAsImage(ctx.document(), ctx.pageIndex()));
        } else {
            if (!imageCandidates.stripCandidates.isEmpty()) {
                candidates.addAll(stitchStripCandidatesByColumn(imageCandidates.stripCandidates));
            }
            if (!imageCandidates.regularCandidates.isEmpty()) {
                candidates.addAll(imageCandidates.regularCandidates);
            }
        }

        if (candidates.isEmpty()) {
            return ExtractionResult.empty();
        }

        if (candidates.size() > MAX_LLM_IMAGE_CANDIDATES) {
            log.info("[{}] page {} - {} image candidates; limiting LLM calls to {} by area",
                    name(), ctx.pageNumber(), candidates.size(), MAX_LLM_IMAGE_CANDIDATES);
            candidates = candidates.stream()
                    .sorted(Comparator.<BufferedImage>comparingLong(img -> (long) img.getWidth() * img.getHeight()).reversed())
                    .limit(MAX_LLM_IMAGE_CANDIDATES)
                    .toList();
        }

        StringBuilder combined = new StringBuilder();
        for (BufferedImage img : candidates) {
            if (img == null) continue;
            if (img.getWidth() < minImagePx || img.getHeight() < minImagePx) {
                log.debug("[{}] page {} - skipping small image {}x{}",
                        name(), ctx.pageNumber(), img.getWidth(), img.getHeight());
                continue;
            }

            log.debug("[{}] page {} - image {}x{}, calling LLM",
                    name(), ctx.pageNumber(), img.getWidth(), img.getHeight());
            String result = callLlm(img, chatClient);
            if (StringUtils.hasText(result) && !result.contains(NO_TEXT_SENTINEL)) {
                combined.append(result).append("\n");
            }
        }

        boolean hasImageText = !combined.isEmpty();
        return ExtractionResult.builder()
                .text(combined.toString().trim())
                .metadata(hasImageText ? Map.of("has_images", true) : Map.of())
                .build();
    }

    private ClassifiedImageCandidates classifyImageCandidates(List<PositionedImage> images, PDPage page) {
        List<PositionedImage> strips = new ArrayList<>();
        List<BufferedImage> output = new ArrayList<>();

        for (PositionedImage pi : images) {
            if (isLikelyPageStrip(pi, page)) {
                strips.add(pi);
            } else if (pi.image() != null) {
                output.add(pi.image());
            }
        }

        return new ClassifiedImageCandidates(strips, output);
    }

    private boolean isLikelyPageStrip(PositionedImage pi, PDPage page) {
        PDRectangle pageSize = page.getMediaBox();
        float pageHeight = pageSize.getHeight();
        float pageWidth = pageSize.getWidth();

        float renderedHeight = Math.abs(pi.height());
        float renderedWidth = Math.abs(pi.width());

        boolean wideEnough = renderedWidth > pageWidth * 0.5f;
        boolean thinEnough = renderedHeight < pageHeight * 0.05f;

        return wideEnough && thinEnough;
    }

    private List<BufferedImage> stitchStripCandidatesByColumn(List<PositionedImage> strips) {
        List<List<PositionedImage>> groups = new ArrayList<>();
        for (PositionedImage strip : strips) {
            boolean added = false;
            for (List<PositionedImage> group : groups) {
                float repX = group.get(0).x();
                if (Math.abs(strip.x() - repX) <= STRIP_X_TOLERANCE) {
                    group.add(strip);
                    added = true;
                    break;
                }
            }
            if (!added) {
                List<PositionedImage> newGroup = new ArrayList<>();
                newGroup.add(strip);
                groups.add(newGroup);
            }
        }

        List<BufferedImage> stitched = new ArrayList<>();
        for (List<PositionedImage> group : groups) {
            group.sort(Comparator.comparingDouble(PositionedImage::y));

            int totalWidth = group.stream().mapToInt(p -> p.image().getWidth()).max().orElse(0);
            int totalHeight = group.stream().mapToInt(p -> p.image().getHeight()).sum();
            if (totalWidth <= 0 || totalHeight <= 0) continue;

            BufferedImage canvas = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = canvas.createGraphics();
            try {
                int y = 0;
                for (PositionedImage pi : group) {
                    graphics.drawImage(pi.image(), 0, y, null);
                    y += pi.image().getHeight();
                }
            } finally {
                graphics.dispose();
            }

            log.debug("Stitched {} strips into {}x{}", group.size(), totalWidth, totalHeight);
            stitched.add(canvas);
        }
        return stitched;
    }

    private List<PositionedImage> extractPositionedImages(PDPage page) throws IOException {
        if (page.getResources() == null) return List.of();

        List<PositionedImage> found = new ArrayList<>();
        PDFGraphicsStreamEngine engine = new PDFGraphicsStreamEngine(page) {
            @Override
            public void drawImage(PDImage pdImage) throws IOException {
                if (!(pdImage instanceof PDImageXObject xObj)) return;
                BufferedImage image = xObj.getImage();
                if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return;

                Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
                found.add(new PositionedImage(image, ctm.getTranslateX(), ctm.getTranslateY(),
                        ctm.getScalingFactorX(), ctm.getScalingFactorY()));
            }

            @Override public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {}
            @Override public void clip(int windingRule) {}
            @Override public void moveTo(float x, float y) {}
            @Override public void lineTo(float x, float y) {}
            @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {}
            @Override public Point2D getCurrentPoint() { return new Point2D.Float(0, 0); }
            @Override public void closePath() {}
            @Override public void endPath() {}
            @Override public void strokePath() {}
            @Override public void fillPath(int windingRule) {}
            @Override public void fillAndStrokePath(int windingRule) {}
            @Override public void shadingFill(COSName shadingName) {}
        };

        engine.processPage(page);
        return found;
    }

    private BufferedImage renderPageAsImage(PDDocument document, int pageIndex) throws IOException {
        return new PDFRenderer(document).renderImageWithDPI(pageIndex, FULL_PAGE_RENDER_DPI);
    }

    static String callLlm(BufferedImage image, ChatClient chatClient) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", baos)) {
                log.warn("Failed to encode image for OCR request.");
                return "";
            }

            Media media = new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(baos.toByteArray()));
            var systemMessage = SystemMessage.builder()
                    .text("""
                    You are a precise OCR engine. Your only job is verbatim text extraction.
            
                    CRITICAL RULES:
                    1. Extract EVERY word, number, and symbol visible — including headers, footers, page numbers, watermarks, captions, and footnotes.
                    2. Never stop early. Process the entire image from top-left to bottom-right without skipping any region.
                    3. Do not summarize, compress, or use "..." to skip repetitive content — extract it fully every time.
                    4. Never correct spelling, grammar, or formatting.
                    5. Never translate — preserve original language and script.
                    6. Use [illegible] for unreadable characters. Never guess.
                    7. Use [SIGNATURE], [STAMP], [GRAPHIC] for non-text visual elements.
                    8. Use [REDACTED] for blacked-out areas.
                    9. Structure rules:
                       - Headings on their own line
                       - Tables: tab-separated columns (\\t), one row per line
                       - Form fields: "Label: Value"
                       - Checkboxes: [X] checked, [ ] unchecked
                    10. Separate document sections (header / body / footer) with a blank line.
                    11. If no text exists anywhere: respond only with [NO TEXT IN IMAGE]
                    12. Output ONLY the raw extracted text. No commentary, no markdown, no explanation.
                    """)
                    .build();

            var userMessage = UserMessage.builder()
                    .text("""
                    Extract every single piece of text visible in this image.
            
                    Important:
                    - Do NOT skip any region — scan top to bottom, left to right, every column and margin.
                    - Do NOT use "..." or similar shortcuts for any reason.
                    - Do NOT stop until the entire image has been processed.
                    - Include watermarks, page numbers, footnotes, and captions.
                    """)
                    .media(media)
                    .build();

            String content = chatClient.prompt()
                    .messages(systemMessage, userMessage)
                    .call()
                    .content();
            return content != null ? content : "";
        } catch (Exception e) {
            log.warn("Vision OCR call failed: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public String name() { return "EmbeddedImageExtractor"; }

    private record ClassifiedImageCandidates(List<PositionedImage> stripCandidates, List<BufferedImage> regularCandidates) {}
}
