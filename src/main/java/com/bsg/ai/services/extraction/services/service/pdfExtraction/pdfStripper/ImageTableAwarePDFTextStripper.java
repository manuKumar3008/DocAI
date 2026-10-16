package com.bsg.ai.services.extraction.services.service.pdfExtraction.pdfStripper;

import com.bsg.ai.services.extraction.services.records.ImageMarker;
import com.bsg.ai.services.extraction.services.records.ImagePlaceholder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

@Slf4j
public class ImageTableAwarePDFTextStripper extends PDFTextStripper {

    // Holds images found on current page, keyed by their Y position
    private final List<ImageMarker> pendingImages = new ArrayList<>();
    private int imageCounter = 0;
    private float lastTextY = -1;
    public static final String IMAGE_MARKER = "IMAGE_";
    private final List<Rectangle2D.Float> excludedRegions = new ArrayList<>();
    private List<Map.Entry<Integer, Rectangle2D.Float>> indexedRegions = new ArrayList<>();
    private Set<Integer> injectedTables = new HashSet<>();
    private float currentY = 0;

    @Getter
    public final List<ImagePlaceholder> imagePlaceholders = new ArrayList<>();

    public ImageTableAwarePDFTextStripper() {
        super();
        setSortByPosition(false);
        setShouldSeparateByBeads(true);
    }

    public void setExcludedRegions(List<Rectangle2D.Float> regions) {
        excludedRegions.clear();
        if (regions != null) {
            excludedRegions.addAll(regions);
        }
    }

    public void setTableRegions(List<Rectangle2D.Float> regions) {
        this.indexedRegions = new ArrayList<>();
        for (int i = 0; i < regions.size(); i++) {
            indexedRegions.add(Map.entry(i, regions.get(i)));
        }
        // Sort top→bottom by minY (smallest Y = top of page in PDF space)
        indexedRegions.sort(Comparator.comparingDouble(e -> e.getValue().getMinY()));
    }

    @Override
    protected void processTextPosition(TextPosition text) {
        if (text == null || isInsideExcludedRegion(text)) {
            return;
        }
        super.processTextPosition(text);
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands)
            throws IOException {

        if ("Do".equals(operator.getName())) {
            COSName objectName = (COSName) operands.get(0);
            PDXObject xobject = getResources().getXObject(objectName);

            if (xobject instanceof PDImageXObject image) {
                Matrix ctm = getGraphicsState()
                        .getCurrentTransformationMatrix();

                BufferedImage bufferedImage = image.getImage();
                if (bufferedImage == null || bufferedImage.getWidth() <= 0 || bufferedImage.getHeight() <= 0) return;

                float x = ctm.getTranslateX();
                float y = ctm.getTranslateY();
                float width = ctm.getScalingFactorX();
                float height = ctm.getScalingFactorY();
                int pageNo = getCurrentPageNo();

                imageCounter++;

                // ✅ Do NOT write to output here.
                //    Just record the image with its visual Y position.
                pendingImages.add(new ImageMarker(
                        imageCounter, pageNo, x, y, width, height
                ));

                imagePlaceholders.add(new ImagePlaceholder(
                        imageCounter, pageNo, bufferedImage, x, y, width, height,
                        image.getWidth(), image.getHeight()
                ));
            }
        } else {
            super.processOperator(operator, operands);
        }
    }

    /**
     * Called by PDFTextStripper after it has sorted ALL TextPositions
     * for the page and is about to write each word/line.
     * <p>
     * We override writeString to intercept each text token,
     * check if any pending image should appear BEFORE it based on Y,
     * and flush those image markers first.
     */
    @Override
    protected void writeString(String text, List<TextPosition> textPositions)
            throws IOException {

        // TextPosition.getY() is in Java2D space: Y=0 at top, increasing downward.
        // CTM image coordinates are in PDF space: Y=0 at bottom, increasing upward.
        // Convert image top edge to Java2D space: imgTopInTextSpace = pageHeight - imgTopInPdfSpace
        float textY = textPositions.isEmpty() ? lastTextY : textPositions.get(0).getY();
        float textX = textPositions.isEmpty() ? 0 : textPositions.get(0).getX();
        float textXLast = textPositions.isEmpty() ? 0 : textPositions.get(textPositions.size() - 1).getX();
        float pageHeight = getCurrentPage().getMediaBox().getHeight();

        if (!pendingImages.isEmpty()) {
            Iterator<ImageMarker> iter = pendingImages.iterator();
            while (iter.hasNext()) {
                var img = iter.next();
                float imgTopInTextSpace = pageHeight - Math.max(img.pageY(), img.pageY() + img.renderedHeight());
                float imgBottomInTextSpace = pageHeight - Math.min(img.pageY(), img.pageY() + img.renderedHeight());
                float imgRight = img.pageX();
                // Flush if text is completely below image (any column),
                // or text has reached the image top and is in a column to the right.

                boolean belowImage = textY >= imgBottomInTextSpace && (textX >= imgRight || textXLast >= imgRight);
                boolean sameRowNextColumn = textY >= imgTopInTextSpace && (textX >= imgRight || textXLast >= imgRight);
                log.trace("[IMG FLUSH before text] text='{}', textY={}, textX={}, textXLast={}, imgTopInTextSpace={}, imgBottomInTextSpace={}, imgRight={}, belowImage={}, sameRowNextColumn={}",
                        text, textY, textX, textXLast, imgTopInTextSpace, imgBottomInTextSpace, imgRight, belowImage, sameRowNextColumn);
                if (belowImage || sameRowNextColumn) {
                    super.writeString(buildMarker(img));
                    iter.remove();
                }
            }
        }
        if (!textPositions.isEmpty()) {
            currentY = textPositions.get(0).getY();
        }

        for (Map.Entry<Integer, Rectangle2D.Float> entry : indexedRegions) {
            int tableIndex = entry.getKey();
            if (injectedTables.contains(tableIndex)) continue;

            Rectangle2D.Float region = entry.getValue();
            // currentY has entered or passed the table region's top edge
            if (currentY >= region.getMinY()) {
                super.writeString("[TABLE:" + tableIndex + "]", Collections.emptyList());
                writeLineSeparator();
                injectedTables.add(tableIndex);
            }
        }

        super.writeString(text, textPositions);
        lastTextY = textY;

        // After writing text, flush images whose position has been fully passed —
        // catches images that sit between this text token and the next, or after the last token.
        if (!pendingImages.isEmpty()) {
            Iterator<ImageMarker> iter = pendingImages.iterator();
            while (iter.hasNext()) {
                ImageMarker img = iter.next();
                float imgTopInTextSpace = pageHeight - Math.max(img.pageY(), img.pageY() + img.renderedHeight());
                float imgBottomInTextSpace = pageHeight - Math.min(img.pageY(), img.pageY() + img.renderedHeight());
                float imgRight = img.pageX();
                boolean belowImage = lastTextY >= imgBottomInTextSpace && textX >= imgRight;
                boolean sameRowNextColumn = lastTextY >= imgTopInTextSpace && textX >= imgRight;
                if (belowImage || sameRowNextColumn) {
                    log.trace("[IMG FLUSH after text] text='{}', textY={}, textX={}, imgTopInTextSpace={}, imgBottomInTextSpace={}, imgRight={}",
                            text, lastTextY, textX, imgTopInTextSpace, imgBottomInTextSpace, imgRight);
                    super.writeString(buildMarker(img));
                    iter.remove();
                }
            }
        }
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        lastTextY = -1;
        imagePlaceholders.clear();
        pendingImages.clear();
        imageCounter = 0;
        injectedTables.clear();
        currentY = 0;
        super.startPage(page);
    }

    /**
     * Called when PDFTextStripper finishes writing a page.
     * Flush any remaining images that appear BELOW all text on the page.
     */
    @Override
    protected void endPage(PDPage page) throws IOException {
        // Flush remaining images (those below all text, or only-image pages)
        for (ImageMarker img : pendingImages) {
            super.writeString(buildMarker(img));
        }
        pendingImages.clear();
        // Inject any table markers that were never triggered
        // (table was at end of page — no text came after it)
        for (Map.Entry<Integer, Rectangle2D.Float> entry : indexedRegions) {
            int tableIndex = entry.getKey();
            if (!injectedTables.contains(tableIndex)) {
                super.writeString("[TABLE:" + tableIndex + "]", Collections.emptyList());
                writeLineSeparator();
                injectedTables.add(tableIndex);
            }
        }
        super.endPage(page);
    }

    private String buildMarker(ImageMarker img) {
        return String.format(
                "\n[%s%d: page=%d, x=%.1f, y=%.1f, w=%.1f, h=%.1f]\n",
                IMAGE_MARKER, img.imageIndex(), img.pageNumber(),
                img.pageX(), img.pageY(), img.renderedWidth(), img.renderedHeight()
        );
    }

    private boolean isInsideExcludedRegion(TextPosition text) {
        if (excludedRegions.isEmpty()) {
            return false;
        }
        float x = text.getXDirAdj();
        float y = text.getYDirAdj();
        for (Rectangle2D.Float region : excludedRegions) {
            if (region != null && region.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

}
