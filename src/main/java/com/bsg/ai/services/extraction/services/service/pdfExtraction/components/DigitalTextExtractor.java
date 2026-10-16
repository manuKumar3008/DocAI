package com.bsg.ai.services.extraction.services.service.pdfExtraction.components;

import com.bsg.ai.services.extraction.services.records.ExtractionResult;
import com.bsg.ai.services.extraction.services.records.ImagePlaceholder;
import com.bsg.ai.services.extraction.services.records.PageExtractionContext;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.PageContentExtractor;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.pdfStripper.ImageTableAwarePDFTextStripper;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.Cell;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.DetectedTable;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.services.PdfTableExtractionEngine;
import com.bsg.ai.services.ocr.service.LlmOcrService;
import com.bsg.ai.services.ocr.service.TesseractOcrService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.bsg.ai.services.extraction.services.service.pdfExtraction.components.RotatedTextExtractor.TOLERANCE;

/**
 * Extracts digital text using ImageAwarePDFTextStripper (text + inline image markers).
 * For each image marker, OCR is attempted using TesseractOcrService (primary).
 * If Tesseract returns no text, LlmOcrService is used as fallback.
 * Strip images (thin horizontal bands) in the same X column are stitched into one
 * combined image before OCR to minimise processing round-trips.
 * If the page contains too many strip/clipped images, this extractor returns an empty
 * result so that a fallback extractor can handle it.
 */
@Slf4j
public class DigitalTextExtractor implements PageContentExtractor {

    private static final int DEFAULT_MIN_IMAGE_PX = 50;
    /** Pages with more strip images than this are deferred to the fallback extractor. */
    private static final int CLIPPED_IMAGE_THRESHOLD = 10;
    /** Max X-distance (page units) between strips considered the same column. */
    private static final float STRIP_X_TOLERANCE = 5f;
    private static final float TABLE_REGION_PADDING = 1.5f;

    private static final Pattern MARKER_PATTERN = Pattern.compile(
            "\\[" + ImageTableAwarePDFTextStripper.IMAGE_MARKER
                    + "(\\d+): page=\\d+, x=-?[\\d.]+, y=-?[\\d.]+, w=-?[\\d.]+, h=-?[\\d.]+]"
    );

    private final ImageTableAwarePDFTextStripper stripper;
    private final PdfTableExtractionEngine tableEngine;
    private final TesseractOcrService tesseractOcrService;
    private final LlmOcrService llmOcrService;
    private final int minImagePx;

    public DigitalTextExtractor(TesseractOcrService tesseractOcrService, LlmOcrService llmOcrService) {
        this(tesseractOcrService, llmOcrService, DEFAULT_MIN_IMAGE_PX);
    }

    public DigitalTextExtractor(TesseractOcrService tesseractOcrService, LlmOcrService llmOcrService, int minImagePx) {
        this.tesseractOcrService = Objects.requireNonNull(tesseractOcrService, "tesseractOcrService is required");
        this.llmOcrService = Objects.requireNonNull(llmOcrService, "llmOcrService is required");
        this.minImagePx = minImagePx > 0 ? minImagePx : DEFAULT_MIN_IMAGE_PX;
        this.tableEngine = new PdfTableExtractionEngine();
        this.stripper = new ImageTableAwarePDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition text) {
                float a = text.getDir();
                if (Math.abs(a) < TOLERANCE || Math.abs(a - 180f) < TOLERANCE) {
                    super.processTextPosition(text);
                }
            }
        };
    }

    @Override
    public ExtractionResult extract(PageExtractionContext ctx, String soFar) throws Exception {
        List<DetectedTable> tables = tableEngine.extractPageTables(ctx.document(), ctx.pageIndex());
        if (!tables.isEmpty()) {
            var regions = resolveTableRegions(tables);      // your existing method, unchanged
            stripper.setExcludedRegions(regions);       // existing — suppresses text
            stripper.setTableRegions(regions);          // new — injects [TABLE:N] markers
        }
        stripper.setShouldSeparateByBeads(true);
        stripper.setStartPage(ctx.pageNumber());
        stripper.setEndPage(ctx.pageNumber());
        String text = stripper.getText(ctx.document());
        text = text.lines().map(String::strip).filter(l -> !l.isEmpty()).collect(Collectors.joining("\n"));
        text = replaceTableMarkers(text, tables);
        // startPage resets imagePlaceholders per page, so no cross-page accumulation
        List<ImagePlaceholder> placeholders = stripper.getImagePlaceholders();

        if (placeholders.isEmpty()) {
            return ExtractionResult.builder()
                    .text(text.trim())
//                    .tables(CollectionUtils.isNotEmpty(tables) ? tables : new ArrayList<>())
                    .metadata(CollectionUtils.isNotEmpty(tables)
                            ? Map.of("has_tables", true, "table_count", tables.size())
                            : Map.of())
                    .build();
        }

        // Classify: thin wide strips (clipped) vs regular images
        List<ImagePlaceholder> strips = new ArrayList<>();
        List<ImagePlaceholder> regular = new ArrayList<>();
        for (ImagePlaceholder ip : placeholders) {
            if (isLikelyStrip(ip, ctx.page())) {
                strips.add(ip);
            } else {
                regular.add(ip);
            }
        }

        // Too many clipped images → entire page is strip-heavy, defer to fallback extractor
        if (strips.size() > CLIPPED_IMAGE_THRESHOLD) {
            log.info("[{}] page {} - {} clipped strip images exceed threshold {}, deferring to fallback extractor",
                    name(), ctx.pageNumber(), strips.size(), CLIPPED_IMAGE_THRESHOLD);
            return ExtractionResult.empty();
        }

        // Build OCR groups: stitch strips per X column (one call per column),
        // regular images are sent individually (one call each).
        List<ImageGroup> groups = new ArrayList<>();
        for (List<ImagePlaceholder> col : groupStripsByColumn(strips)) {
            ImageGroup g = stitchGroup(col);
            if (g != null) groups.add(g);
        }
        for (ImagePlaceholder ip : regular) {
            if (ip.bufferedImage() != null) {
                groups.add(new ImageGroup(List.of(ip.imageIndex()), ip.bufferedImage()));
            }
        }

        // One OCR call per group → build imageIndex → replacement-text map
        Map<Integer, String> replacements = new HashMap<>();
        for (ImageGroup group : groups) {
            BufferedImage img = group.image();
            if (img.getWidth() < minImagePx || img.getHeight() < minImagePx) {
                log.debug("[{}] page {} - skipping small image {}x{}",
                        name(), ctx.pageNumber(), img.getWidth(), img.getHeight());
                group.indices().forEach(idx -> replacements.put(idx, ""));
                continue;
            }
            log.debug("[{}] page {} - performing OCR for {} image(s), {}x{}",
                    name(), ctx.pageNumber(), group.indices().size(), img.getWidth(), img.getHeight());

            String ocrText = performOcr(img, ctx.pageNumber());
            String resolved = StringUtils.hasText(ocrText) ? ocrText : "";
            // First index gets the extracted text; stitched companions are collapsed to empty
            boolean first = true;
            for (int idx : group.indices()) {
                replacements.put(idx, first ? resolved : "");
                first = false;
            }
        }

        String finalText = replaceMarkers(text, replacements);
        finalText = finalText.lines().map(String::strip).filter(l -> !l.isEmpty()).collect(Collectors.joining("\n"));
        return ExtractionResult.builder()
                .text(finalText.trim())
//                .tables(CollectionUtils.isNotEmpty(tables) ? tables : new ArrayList<>())
                .metadata(CollectionUtils.isNotEmpty(tables)
                        ? Map.of("has_tables", true, "table_count", tables.size())
                        : Map.of())
                .build();
    }

    private String replaceTableMarkers(String text, List<DetectedTable> tables) {
        for (int i = 0; i < tables.size(); i++) {
            text = text.replace("[TABLE:" + i + "]", tables.get(i).toMarkdown());
        }
        return text;
    }

    /**
     * Returns a copy of the image with a white background composited underneath.
     * A no-op for images that already have no alpha channel.
     */
    private BufferedImage ensureWhiteBackground(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return image;
        }
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = result.createGraphics();
        try {
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
            g2d.drawImage(image, 0, 0, null);
        } finally {
            g2d.dispose();
        }
        return result;
    }

    /**
     * Tries Tesseract first; falls back to LLM vision if Tesseract returns blank text.
     */
    private String performOcr(BufferedImage image, int pageNumber) {
        image = ensureWhiteBackground(image);
        try {
            String llmText = llmOcrService.extractText(image);
            log.debug("[{}] page {} - LLM OCR fallback completed", name(), pageNumber);
            return llmText;
        } catch (Exception e) {
            log.error("[{}] page {} - LLM OCR fallback also failed: {}", name(), pageNumber, e.getMessage());
        }

        try {
            String tesseractText = tesseractOcrService.extractText(image, null);
            if (StringUtils.hasText(tesseractText)) {
                log.debug("[{}] page {} - Tesseract OCR succeeded", name(), pageNumber);
                return tesseractText.strip();
            }
            log.debug("[{}] page {} - Tesseract returned empty, falling back to LLM OCR", name(), pageNumber);
        } catch (Exception e) {
            log.warn("[{}] page {} - Tesseract OCR failed ({}), falling back to LLM OCR", name(), pageNumber, e.getMessage());
        }

        return "";
    }

    /**
     * A strip is a wide but thin image — typically a sliced background or decorative band.
     */
    private boolean isLikelyStrip(ImagePlaceholder ip, PDPage page) {
        PDRectangle box = page.getMediaBox();
        float w = Math.abs(ip.renderedWidth());
        float h = Math.abs(ip.renderedHeight());
        return w > box.getWidth() * 0.5f && h < box.getHeight() * 0.05f;
    }

    /** Group strip images by X column (strips within STRIP_X_TOLERANCE share a group). */
    private List<List<ImagePlaceholder>> groupStripsByColumn(List<ImagePlaceholder> strips) {
        List<List<ImagePlaceholder>> groups = new ArrayList<>();
        for (ImagePlaceholder strip : strips) {
            boolean added = false;
            for (List<ImagePlaceholder> group : groups) {
                if (Math.abs(strip.pageX() - group.get(0).pageX()) <= STRIP_X_TOLERANCE) {
                    group.add(strip);
                    added = true;
                    break;
                }
            }
            if (!added) {
                List<ImagePlaceholder> g = new ArrayList<>();
                g.add(strip);
                groups.add(g);
            }
        }
        return groups;
    }

    /**
     * Stitch a column of strips into one BufferedImage (top-to-bottom).
     * In PDF space Y increases upward, so descending pageY = visual top-to-bottom order.
     */
    private ImageGroup stitchGroup(List<ImagePlaceholder> group) {
        group.sort(Comparator.comparingDouble(ImagePlaceholder::pageY).reversed());
        List<Integer> indices = group.stream().map(ImagePlaceholder::imageIndex).toList();
        int totalWidth = group.stream().mapToInt(ip -> ip.bufferedImage().getWidth()).max().orElse(0);
        int totalHeight = group.stream().mapToInt(ip -> ip.bufferedImage().getHeight()).sum();
        if (totalWidth <= 0 || totalHeight <= 0) return null;

        BufferedImage canvas = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = canvas.createGraphics();
        try {
            int y = 0;
            for (ImagePlaceholder ip : group) {
                g2d.drawImage(ip.bufferedImage(), 0, y, null);
                y += ip.bufferedImage().getHeight();
            }
        } finally {
            g2d.dispose();
        }
        log.debug("[{}] stitched {} strips into {}x{}", name(), group.size(), totalWidth, totalHeight);
        return new ImageGroup(indices, canvas);
    }

    /** Replace [IMAGE_N: ...] markers in the extracted text with OCR-derived content. */
    private String replaceMarkers(String text, Map<Integer, String> replacements) {
        Matcher matcher = MARKER_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            int idx = Integer.parseInt(matcher.group(1));
            // Keep original marker if no replacement was produced (e.g. image skipped)
            String replacement = replacements.getOrDefault(idx, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @Override
    public String name() { return "DigitalTextExtractor"; }

    private List<Rectangle2D.Float> resolveTableRegions(List<DetectedTable> tables) {
        List<Rectangle2D.Float> regions = new ArrayList<>();
        for (DetectedTable table : tables) {
            Rectangle2D.Float region = toTableRegion(table);
            if (region != null) {
                regions.add(region);
            }
        }
        return regions;
    }

    private Rectangle2D.Float toTableRegion(DetectedTable table) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        for (List<Cell> row : table.getRows()) {
            for (Cell cell : row) {
                minX = Math.min(minX, cell.getX());
                minY = Math.min(minY, cell.getY());
                maxX = Math.max(maxX, cell.getX() + cell.getWidth());
                maxY = Math.max(maxY, cell.getY() + cell.getHeight());
            }
        }

        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(maxX) || !Float.isFinite(maxY)) {
            return null;
        }

        return new Rectangle2D.Float(
                minX - TABLE_REGION_PADDING,
                minY - TABLE_REGION_PADDING,
                Math.max(0.5f, (maxX - minX) + (2 * TABLE_REGION_PADDING)),
                Math.max(0.5f, (maxY - minY) + (2 * TABLE_REGION_PADDING))
        );
    }

    private record ImageGroup(List<Integer> indices, BufferedImage image) {}
}
