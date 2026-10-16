package com.bsg.ai.services.extraction.services.service.pdfExtraction.table.strippers;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;

import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts explicit line segments from page drawing operators.
 * Coordinates remain in PDF space (origin = bottom-left).
 */
public class PageVectorLineExtractor extends PDFGraphicsStreamEngine {

    //Records -> Data careers ------------------------
    public record HorizontalLine(float y, float x1, float x2){}
    public record VerticalLine(float x, float y1, float y2){}
//    -------------------------------------------

    private static final float MIN_LINE_LENGTH = 6.0f;
    private static final float ORIENTATION_TOLERANCE = 1.8f;

    private final List<HorizontalLine> horizontalLines = new ArrayList<>();
    private final List<VerticalLine> verticalLines = new ArrayList<>();
    private final GeneralPath currentPath = new GeneralPath();
    private float currentX;
    private float currentY;

    public PageVectorLineExtractor(PDPage page) {
        super(page);
    }

    public List<HorizontalLine> getHorizontalLines() {
        return horizontalLines;
    }

    public List<VerticalLine> getVerticalLines() {
        return verticalLines;
    }

    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        classifySegment((float) p0.getX(), (float) p0.getY(), (float) p1.getX(), (float) p1.getY());
        classifySegment((float) p1.getX(), (float) p1.getY(), (float) p2.getX(), (float) p2.getY());
        classifySegment((float) p2.getX(), (float) p2.getY(), (float) p3.getX(), (float) p3.getY());
        classifySegment((float) p3.getX(), (float) p3.getY(), (float) p0.getX(), (float) p0.getY());
    }

    @Override
    public void drawImage(PDImage pdImage) {
    }

    @Override
    public void clip(int windingRule) {
    }

    @Override
    public void moveTo(float x, float y) {
        currentX = x;
        currentY = y;
        currentPath.moveTo(x, y);
    }

    @Override
    public void lineTo(float x, float y) {
        currentX = x;
        currentY = y;
        currentPath.lineTo(x, y);
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        currentX = x3;
        currentY = y3;
        currentPath.curveTo(x1, y1, x2, y2, x3, y3);
    }

    @Override
    public Point2D getCurrentPoint() {
        return new Point2D.Float(currentX, currentY);
    }

    @Override
    public void closePath() {
        currentPath.closePath();
    }

    @Override
    public void endPath() {
        currentPath.reset();
    }

    @Override
    public void strokePath() {
        extractFromCurrentPath();
        currentPath.reset();
    }

    @Override
    public void fillPath(int windingRule) {
        extractFromCurrentPath();
        currentPath.reset();
    }

    @Override
    public void fillAndStrokePath(int windingRule) {
        extractFromCurrentPath();
        currentPath.reset();
    }

    @Override
    public void shadingFill(COSName shadingName) {
    }

    private void extractFromCurrentPath() {
        PathIterator iterator = currentPath.getPathIterator(null);
        float[] coords = new float[6];
        float moveX = 0f;
        float moveY = 0f;
        float lastX = 0f;
        float lastY = 0f;

        while (!iterator.isDone()) {
            int type = iterator.currentSegment(coords);
            if (type == PathIterator.SEG_MOVETO) {
                moveX = coords[0];
                moveY = coords[1];
                lastX = moveX;
                lastY = moveY;
            } else if (type == PathIterator.SEG_LINETO) {
                classifySegment(lastX, lastY, coords[0], coords[1]);
                lastX = coords[0];
                lastY = coords[1];
            } else if (type == PathIterator.SEG_CLOSE) {
                classifySegment(lastX, lastY, moveX, moveY);
            }
            iterator.next();
        }
    }

    private void classifySegment(float x1, float y1, float x2, float y2) {
        float dx = Math.abs(x2 - x1);
        float dy = Math.abs(y2 - y1);

        if (dx >= MIN_LINE_LENGTH && dy <= ORIENTATION_TOLERANCE) {
            horizontalLines.add(new HorizontalLine((y1 + y2) / 2f, Math.min(x1, x2), Math.max(x1, x2)));
            return;
        }
        if (dy >= MIN_LINE_LENGTH && dx <= ORIENTATION_TOLERANCE) {
            verticalLines.add(new VerticalLine((x1 + x2) / 2f, Math.min(y1, y2), Math.max(y1, y2)));
        }
    }
}
