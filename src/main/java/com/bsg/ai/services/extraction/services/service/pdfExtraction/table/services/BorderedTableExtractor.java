package com.bsg.ai.services.extraction.services.service.pdfExtraction.table.services;

import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.Cell;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.DetectedTable;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.strippers.PageVectorLineExtractor;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripperByArea;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class BorderedTableExtractor {

    private static final float SNAP_TOLERANCE = 4.5f;
    private static final float LINE_JOIN_TOLERANCE = 3.0f;
    private static final float MIN_BOUNDARY_COVERAGE = 0.70f;
    private static final float MIN_GRID_GAP = 6.0f;
    private static final float MIN_BOUNDARY_COVERAGE_RATIO = 0.18f;
    private static final int MIN_BOUNDARY_INTERSECTIONS = 2;
    private static final int MIN_TABLE_COLUMNS = 2;
    private static final int MIN_TABLE_ROWS = 1;
    private static final float GRID_INTERNAL_BOUNDARY_COVERAGE_RATIO = 0.50f;
    private static final int GRID_INTERNAL_BOUNDARY_MIN_INTERSECTIONS = 3;
    private static final float OUTER_BOX_EDGE_MARGIN_RATIO = 0.12f;
    private static final float OUTER_BOX_MIN_PAGE_COVERAGE_RATIO = 0.75f;
    private static final float OUTER_BOX_LINE_COVERAGE_RATIO = 0.85f;
    private static final float OUTER_BOX_MIN_AREA_RATIO = 0.35f;
    private static final float OUTER_BOX_INNER_GAP = 8.0f;

    public List<DetectedTable> extractPageTables(PDPage page, int pageIndex, float pageWidth, float pageHeight) throws IOException {
        PageVectorLineExtractor lineExtractor = new PageVectorLineExtractor(page);
        lineExtractor.processPage(page);

        List<PageVectorLineExtractor.HorizontalLine> horizontalLines =
                normalizeHorizontal(lineExtractor.getHorizontalLines());
        List<PageVectorLineExtractor.VerticalLine> verticalLines =
                normalizeVertical(lineExtractor.getVerticalLines());
        FrameRemovalResult frameRemovalResult = removeOutermostDecorativeBox(horizontalLines, verticalLines, pageWidth, pageHeight);
        horizontalLines = frameRemovalResult.horizontal();
        verticalLines = frameRemovalResult.vertical();

        if (horizontalLines.size() < 2 || verticalLines.size() < 2) {
            return Collections.emptyList();
        }

        List<LineComponent> components = findLineComponents(horizontalLines, verticalLines);
        if (components.isEmpty()) {
            return Collections.emptyList();
        }

        List<DetectedTable> tables = new ArrayList<>();
        int tableIndex = 0;
        for (LineComponent component : components) {
            if (component.horizontal.size() < 2 || component.vertical.size() < 2) {
                continue;
            }

            List<Float> xs = selectReliableVerticalBoundaries(component);
            List<Float> ys = selectReliableHorizontalBoundaries(component);
            xs = enforceMinimumBoundaryGap(xs, MIN_GRID_GAP);
            ys = enforceMinimumBoundaryGap(ys, MIN_GRID_GAP);
            ys.sort(Comparator.reverseOrder());

            if (xs.size() < 2 || ys.size() < 2) {
                continue;
            }
            if (xs.size() - 1 < MIN_TABLE_COLUMNS) {
                continue;
            }
            if (ys.size() - 1 < MIN_TABLE_ROWS) {
                continue;
            }
            if (!isBorderedGridCandidate(component, xs, ys)) {
                continue;
            }

            DetectedTable table = buildGridTable(page, pageHeight, pageIndex, tableIndex, xs, ys, component.horizontal, component.vertical);
            if (!table.getRows().isEmpty()) {
                tables.add(table);
                tableIndex++;
            }
        }
        return tables;
    }

    boolean hasBorderedGrid(PDPage page) throws IOException {
        float pageWidth = page.getMediaBox().getWidth();
        float pageHeight = page.getMediaBox().getHeight();

        PageVectorLineExtractor lineExtractor = new PageVectorLineExtractor(page);
        lineExtractor.processPage(page);

        List<PageVectorLineExtractor.HorizontalLine> hLines = normalizeHorizontal(lineExtractor.getHorizontalLines());
        List<PageVectorLineExtractor.VerticalLine> vLines = normalizeVertical(lineExtractor.getVerticalLines());
        FrameRemovalResult frameRemovalResult = removeOutermostDecorativeBox(hLines, vLines, pageWidth, pageHeight);
        hLines = frameRemovalResult.horizontal();
        vLines = frameRemovalResult.vertical();

        if (hLines.size() < 2 || vLines.size() < 2) {
            return false;
        }

        List<LineComponent> components = findLineComponents(hLines, vLines);
        for (LineComponent component : components) {
            List<Float> xs = selectReliableVerticalBoundaries(component);
            List<Float> ys = selectReliableHorizontalBoundaries(component);
            xs = enforceMinimumBoundaryGap(xs, MIN_GRID_GAP);
            ys = enforceMinimumBoundaryGap(ys, MIN_GRID_GAP);
            if (xs.size() < 3 || ys.size() < 3) {
                continue;
            }
            if (isBorderedGridCandidate(component, xs, ys)) {
                return true;
            }
        }
        return false;
    }

    private FrameRemovalResult removeOutermostDecorativeBox(List<PageVectorLineExtractor.HorizontalLine> horizontal,
                                                            List<PageVectorLineExtractor.VerticalLine> vertical,
                                                            float pageWidth, float pageHeight) {
        if (horizontal.isEmpty() || vertical.isEmpty()) {
            return new FrameRemovalResult(horizontal, vertical);
        }

        float minX = vertical.stream().map(PageVectorLineExtractor.VerticalLine::x).min(Float::compareTo).orElse(0f);
        float maxX = vertical.stream().map(PageVectorLineExtractor.VerticalLine::x).max(Float::compareTo).orElse(0f);
        float minY = horizontal.stream().map(PageVectorLineExtractor.HorizontalLine::y).min(Float::compareTo).orElse(0f);
        float maxY = horizontal.stream().map(PageVectorLineExtractor.HorizontalLine::y).max(Float::compareTo).orElse(0f);

        float frameWidth = maxX - minX;
        float frameHeight = maxY - minY;
        if (frameWidth <= 0 || frameHeight <= 0) {
            return new FrameRemovalResult(horizontal, vertical);
        }

        float edgeMarginX = pageWidth * OUTER_BOX_EDGE_MARGIN_RATIO;
        float edgeMarginY = pageHeight * OUTER_BOX_EDGE_MARGIN_RATIO;
        boolean nearPageEdges = minX <= edgeMarginX
                && maxX >= (pageWidth - edgeMarginX)
                && minY <= edgeMarginY
                && maxY >= (pageHeight - edgeMarginY);
        if (!nearPageEdges) {
            return new FrameRemovalResult(horizontal, vertical);
        }

        boolean largeEnough = frameWidth >= pageWidth * OUTER_BOX_MIN_PAGE_COVERAGE_RATIO
                && frameHeight >= pageHeight * OUTER_BOX_MIN_PAGE_COVERAGE_RATIO
                && ((frameWidth * frameHeight) / Math.max(1f, pageWidth * pageHeight)) >= OUTER_BOX_MIN_AREA_RATIO;
        if (!largeEnough) {
            return new FrameRemovalResult(horizontal, vertical);
        }

        float topCoverage = mergedHorizontalCoverageAtY(horizontal, maxY);
        float bottomCoverage = mergedHorizontalCoverageAtY(horizontal, minY);
        float leftCoverage = mergedVerticalCoverageAtX(vertical, minX);
        float rightCoverage = mergedVerticalCoverageAtX(vertical, maxX);
        boolean hasFrameEdges = topCoverage >= frameWidth * OUTER_BOX_LINE_COVERAGE_RATIO
                && bottomCoverage >= frameWidth * OUTER_BOX_LINE_COVERAGE_RATIO
                && leftCoverage >= frameHeight * OUTER_BOX_LINE_COVERAGE_RATIO
                && rightCoverage >= frameHeight * OUTER_BOX_LINE_COVERAGE_RATIO;
        if (!hasFrameEdges) {
            return new FrameRemovalResult(horizontal, vertical);
        }

        int interiorHorizontal = 0;
        for (PageVectorLineExtractor.HorizontalLine line : horizontal) {
            if (line.y() > minY + OUTER_BOX_INNER_GAP && line.y() < maxY - OUTER_BOX_INNER_GAP) {
                interiorHorizontal++;
            }
        }
        int interiorVertical = 0;
        for (PageVectorLineExtractor.VerticalLine line : vertical) {
            if (line.x() > minX + OUTER_BOX_INNER_GAP && line.x() < maxX - OUTER_BOX_INNER_GAP) {
                interiorVertical++;
            }
        }
        if (interiorHorizontal < 2 || interiorVertical < 1) {
            return new FrameRemovalResult(horizontal, vertical);
        }

        List<PageVectorLineExtractor.HorizontalLine> filteredHorizontal = horizontal.stream()
                .filter(line -> !isFrameHorizontal(line, minY, maxY, minX, maxX, frameWidth))
                .collect(Collectors.toList());
        List<PageVectorLineExtractor.VerticalLine> filteredVertical = vertical.stream()
                .filter(line -> !isFrameVertical(line, minX, maxX, minY, maxY, frameHeight))
                .collect(Collectors.toList());

        return new FrameRemovalResult(filteredHorizontal, filteredVertical);
    }

    private boolean isFrameHorizontal(PageVectorLineExtractor.HorizontalLine line,
                                      float minY, float maxY, float minX, float maxX, float frameWidth) {
        boolean nearTopOrBottom = Math.abs(line.y() - minY) <= SNAP_TOLERANCE
                || Math.abs(line.y() - maxY) <= SNAP_TOLERANCE;
        if (!nearTopOrBottom) {
            return false;
        }
        float overlap = Math.min(maxX, line.x2()) - Math.max(minX, line.x1());
        return overlap >= frameWidth * 0.60f;
    }

    private boolean isFrameVertical(PageVectorLineExtractor.VerticalLine line,
                                    float minX, float maxX, float minY, float maxY, float frameHeight) {
        boolean nearLeftOrRight = Math.abs(line.x() - minX) <= SNAP_TOLERANCE
                || Math.abs(line.x() - maxX) <= SNAP_TOLERANCE;
        if (!nearLeftOrRight) {
            return false;
        }
        float overlap = Math.min(maxY, line.y2()) - Math.max(minY, line.y1());
        return overlap >= frameHeight * 0.60f;
    }

    private DetectedTable buildGridTable(PDPage page, float pageHeight, int pageIndex, int tableIndex,
                                         List<Float> xs, List<Float> ys,
                                         List<PageVectorLineExtractor.HorizontalLine> horizontal,
                                         List<PageVectorLineExtractor.VerticalLine> vertical) throws IOException {
        int rowCount = ys.size() - 1;
        int colCount = xs.size() - 1;
        if (rowCount <= 0 || colCount <= 0) {
            return new DetectedTable(pageIndex, tableIndex);
        }

        boolean[][] covered = new boolean[rowCount][colCount];
        Map<Integer, List<Cell>> logicalRows = new TreeMap<>();
        List<Cell> owners = new ArrayList<>();

        for (int r = 0; r < rowCount; r++) {
            float rowTop = ys.get(r);
            float rowBottom = ys.get(r + 1);
            for (int c = 0; c < colCount; c++) {
                if (covered[r][c]) {
                    continue;
                }

                int colSpan = 1;
                while (c + colSpan < colCount) {
                    float boundaryX = xs.get(c + colSpan);
                    if (hasVerticalBoundary(vertical, boundaryX, rowBottom, rowTop)) {
                        break;
                    }
                    colSpan++;
                }

                int rowSpan = 1;
                while (r + rowSpan < rowCount) {
                    float boundaryY = ys.get(r + rowSpan);
                    boolean hasBoundary = false;
                    for (int cc = c; cc < c + colSpan; cc++) {
                        if (hasHorizontalBoundary(horizontal, boundaryY, xs.get(cc), xs.get(cc + 1))) {
                            hasBoundary = true;
                            break;
                        }
                    }
                    if (hasBoundary) {
                        break;
                    }
                    rowSpan++;
                }

                for (int rr = r; rr < r + rowSpan; rr++) {
                    for (int cc = c; cc < c + colSpan; cc++) {
                        covered[rr][cc] = true;
                    }
                }

                float left = xs.get(c);
                float right = xs.get(c + colSpan);
                float top = ys.get(r);
                float bottom = ys.get(r + rowSpan);

                float normX = left;
                float normY = pageHeight - top;
                float width = right - left;
                float height = top - bottom;

                Cell cell = new Cell("", r, c, rowSpan, colSpan, normX, normY, width, height);
                logicalRows.computeIfAbsent(r, key -> new ArrayList<>()).add(cell);
                owners.add(cell);
            }
        }

        extractCellTextByArea(page, pageHeight, owners);

        DetectedTable table = new DetectedTable(pageIndex, tableIndex);
        for (List<Cell> row : logicalRows.values()) {
            row.sort(Comparator.comparingInt(Cell::getColIndex));
            table.addRow(row);
        }
        return table;
    }

    private void extractCellTextByArea(PDPage page, float pageHeight, List<Cell> owners) throws IOException {
        PDFTextStripperByArea byArea = new PDFTextStripperByArea();
        byArea.setSortByPosition(true);

        Map<String, Cell> regionToCell = new LinkedHashMap<>();
        for (int i = 0; i < owners.size(); i++) {
            Cell cell = owners.get(i);
            float x = cell.getX();
            float yTopPdf = pageHeight - cell.getY();
            float yTopLeft = pageHeight - yTopPdf;
            float width = Math.max(0.5f, cell.getWidth());
            float height = Math.max(0.5f, cell.getHeight());

            Rectangle2D.Float region = new Rectangle2D.Float(x, yTopLeft, width, height);
            String regionName = "cell_" + i;
            byArea.addRegion(regionName, region);
            regionToCell.put(regionName, cell);
        }

        byArea.extractRegions(page);

        for (Map.Entry<String, Cell> entry : regionToCell.entrySet()) {
            String text = byArea.getTextForRegion(entry.getKey());
            entry.getValue().setText(normalizeExtractedText(text));
        }
    }

    private String normalizeExtractedText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        return normalized.replaceAll("[\\t\\f]+", " ");
    }

    private boolean hasVerticalBoundary(List<PageVectorLineExtractor.VerticalLine> vertical,
                                        float x, float y1, float y2) {
        float minY = Math.min(y1, y2);
        float maxY = Math.max(y1, y2);
        float span = maxY - minY;
        if (span <= 0f) {
            return false;
        }
        for (PageVectorLineExtractor.VerticalLine line : vertical) {
            if (Math.abs(line.x() - x) > SNAP_TOLERANCE) {
                continue;
            }
            float overlap = Math.min(maxY, line.y2()) - Math.max(minY, line.y1());
            if (overlap >= span * MIN_BOUNDARY_COVERAGE) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHorizontalBoundary(List<PageVectorLineExtractor.HorizontalLine> horizontal,
                                          float y, float x1, float x2) {
        float minX = Math.min(x1, x2);
        float maxX = Math.max(x1, x2);
        float span = maxX - minX;
        if (span <= 0f) {
            return false;
        }
        for (PageVectorLineExtractor.HorizontalLine line : horizontal) {
            if (Math.abs(line.y() - y) > SNAP_TOLERANCE) {
                continue;
            }
            float overlap = Math.min(maxX, line.x2()) - Math.max(minX, line.x1());
            if (overlap >= span * MIN_BOUNDARY_COVERAGE) {
                return true;
            }
        }
        return false;
    }

    private List<PageVectorLineExtractor.HorizontalLine> normalizeHorizontal(
            List<PageVectorLineExtractor.HorizontalLine> input) {
        if (input.isEmpty()) {
            return Collections.emptyList();
        }
        List<Float> ys = input.stream().map(PageVectorLineExtractor.HorizontalLine::y).collect(Collectors.toList());
        List<Float> snappedY = snapAndDeduplicate(ys, SNAP_TOLERANCE);
        Map<Float, List<PageVectorLineExtractor.HorizontalLine>> grouped = new HashMap<>();

        for (PageVectorLineExtractor.HorizontalLine h : input) {
            float y = nearest(snappedY, h.y());
            grouped.computeIfAbsent(y, key -> new ArrayList<>()).add(new PageVectorLineExtractor.HorizontalLine(y, h.x1(), h.x2()));
        }

        List<PageVectorLineExtractor.HorizontalLine> out = new ArrayList<>();
        for (Map.Entry<Float, List<PageVectorLineExtractor.HorizontalLine>> entry : grouped.entrySet()) {
            List<PageVectorLineExtractor.HorizontalLine> lines = entry.getValue();
            lines.sort(Comparator.comparingDouble(PageVectorLineExtractor.HorizontalLine::x1));
            float curX1 = lines.get(0).x1();
            float curX2 = lines.get(0).x2();
            for (int i = 1; i < lines.size(); i++) {
                PageVectorLineExtractor.HorizontalLine line = lines.get(i);
                if (line.x1() <= curX2 + LINE_JOIN_TOLERANCE) {
                    curX2 = Math.max(curX2, line.x2());
                } else {
                    out.add(new PageVectorLineExtractor.HorizontalLine(entry.getKey(), curX1, curX2));
                    curX1 = line.x1();
                    curX2 = line.x2();
                }
            }
            out.add(new PageVectorLineExtractor.HorizontalLine(entry.getKey(), curX1, curX2));
        }
        return out;
    }

    private List<PageVectorLineExtractor.VerticalLine> normalizeVertical(
            List<PageVectorLineExtractor.VerticalLine> input) {
        if (input.isEmpty()) {
            return Collections.emptyList();
        }
        List<Float> xs = input.stream().map(PageVectorLineExtractor.VerticalLine::x).collect(Collectors.toList());
        List<Float> snappedX = snapAndDeduplicate(xs, SNAP_TOLERANCE);
        Map<Float, List<PageVectorLineExtractor.VerticalLine>> grouped = new HashMap<>();

        for (PageVectorLineExtractor.VerticalLine v : input) {
            float x = nearest(snappedX, v.x());
            grouped.computeIfAbsent(x, key -> new ArrayList<>()).add(new PageVectorLineExtractor.VerticalLine(x, v.y1(), v.y2()));
        }

        List<PageVectorLineExtractor.VerticalLine> out = new ArrayList<>();
        for (Map.Entry<Float, List<PageVectorLineExtractor.VerticalLine>> entry : grouped.entrySet()) {
            List<PageVectorLineExtractor.VerticalLine> lines = entry.getValue();
            lines.sort(Comparator.comparingDouble(PageVectorLineExtractor.VerticalLine::y1));
            float curY1 = lines.get(0).y1();
            float curY2 = lines.get(0).y2();
            for (int i = 1; i < lines.size(); i++) {
                PageVectorLineExtractor.VerticalLine line = lines.get(i);
                if (line.y1() <= curY2 + LINE_JOIN_TOLERANCE) {
                    curY2 = Math.max(curY2, line.y2());
                } else {
                    out.add(new PageVectorLineExtractor.VerticalLine(entry.getKey(), curY1, curY2));
                    curY1 = line.y1();
                    curY2 = line.y2();
                }
            }
            out.add(new PageVectorLineExtractor.VerticalLine(entry.getKey(), curY1, curY2));
        }
        return out;
    }

    private List<LineComponent> findLineComponents(List<PageVectorLineExtractor.HorizontalLine> horizontal,
                                                   List<PageVectorLineExtractor.VerticalLine> vertical) {
        int total = horizontal.size() + vertical.size();
        UnionFind uf = new UnionFind(total);

        for (int i = 0; i < horizontal.size(); i++) {
            for (int j = i + 1; j < horizontal.size(); j++) {
                if (Math.abs(horizontal.get(i).y() - horizontal.get(j).y()) <= SNAP_TOLERANCE
                        && overlap(horizontal.get(i).x1(), horizontal.get(i).x2(), horizontal.get(j).x1(), horizontal.get(j).x2()) >= -LINE_JOIN_TOLERANCE) {
                    uf.union(i, j);
                }
            }
        }

        for (int i = 0; i < vertical.size(); i++) {
            for (int j = i + 1; j < vertical.size(); j++) {
                if (Math.abs(vertical.get(i).x() - vertical.get(j).x()) <= SNAP_TOLERANCE
                        && overlap(vertical.get(i).y1(), vertical.get(i).y2(), vertical.get(j).y1(), vertical.get(j).y2()) >= -LINE_JOIN_TOLERANCE) {
                    uf.union(horizontal.size() + i, horizontal.size() + j);
                }
            }
        }

        for (int h = 0; h < horizontal.size(); h++) {
            PageVectorLineExtractor.HorizontalLine hl = horizontal.get(h);
            for (int v = 0; v < vertical.size(); v++) {
                PageVectorLineExtractor.VerticalLine vl = vertical.get(v);
                boolean crossesX = vl.x() >= hl.x1() - SNAP_TOLERANCE && vl.x() <= hl.x2() + SNAP_TOLERANCE;
                boolean crossesY = hl.y() >= vl.y1() - SNAP_TOLERANCE && hl.y() <= vl.y2() + SNAP_TOLERANCE;
                if (crossesX && crossesY) {
                    uf.union(h, horizontal.size() + v);
                }
            }
        }

        Map<Integer, LineComponent> components = new HashMap<>();
        for (int i = 0; i < horizontal.size(); i++) {
            int root = uf.find(i);
            components.computeIfAbsent(root, key -> new LineComponent()).horizontal.add(horizontal.get(i));
        }
        for (int i = 0; i < vertical.size(); i++) {
            int root = uf.find(horizontal.size() + i);
            components.computeIfAbsent(root, key -> new LineComponent()).vertical.add(vertical.get(i));
        }

        return components.values().stream()
                .filter(component -> component.horizontal.size() >= 2 && component.vertical.size() >= 2)
                .sorted(Comparator.comparingDouble(LineComponent::topY).reversed())
                .collect(Collectors.toList());
    }

    private float overlap(float a1, float a2, float b1, float b2) {
        return Math.min(a2, b2) - Math.max(a1, b1);
    }

    private List<Float> snapAndDeduplicate(List<Float> values, float tolerance) {
        if (values.isEmpty()) {
            return Collections.emptyList();
        }
        List<Float> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        List<Float> snapped = new ArrayList<>();
        float sum = sorted.get(0);
        int count = 1;
        float clusterStart = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            float value = sorted.get(i);
            if (Math.abs(value - clusterStart) <= tolerance) {
                sum += value;
                count++;
                continue;
            }
            snapped.add(sum / count);
            clusterStart = value;
            sum = value;
            count = 1;
        }
        snapped.add(sum / count);
        return snapped;
    }

    private List<Float> enforceMinimumBoundaryGap(List<Float> boundaries, float minGap) {
        if (boundaries.size() < 3) {
            return boundaries;
        }
        List<Float> sorted = new ArrayList<>(boundaries);
        Collections.sort(sorted);

        List<Float> collapsed = new ArrayList<>();
        float sum = sorted.get(0);
        int count = 1;
        float previous = sorted.get(0);

        for (int i = 1; i < sorted.size(); i++) {
            float value = sorted.get(i);
            if (value - previous < minGap) {
                sum += value;
                count++;
            } else {
                collapsed.add(sum / count);
                sum = value;
                count = 1;
            }
            previous = value;
        }
        collapsed.add(sum / count);
        return collapsed;
    }

    private float nearest(List<Float> values, float target) {
        float nearest = values.get(0);
        float distance = Math.abs(nearest - target);
        for (int i = 1; i < values.size(); i++) {
            float candidate = values.get(i);
            float candidateDistance = Math.abs(candidate - target);
            if (candidateDistance < distance) {
                nearest = candidate;
                distance = candidateDistance;
            }
        }
        return nearest;
    }

    private List<Float> selectReliableHorizontalBoundaries(LineComponent component) {
        List<Float> ys = snapAndDeduplicate(
                component.horizontal.stream().map(PageVectorLineExtractor.HorizontalLine::y).collect(Collectors.toList()),
                SNAP_TOLERANCE
        );
        if (ys.size() <= 2) {
            return ys;
        }

        float minX = Float.MAX_VALUE;
        float maxX = Float.NEGATIVE_INFINITY;
        for (PageVectorLineExtractor.VerticalLine v : component.vertical) {
            minX = Math.min(minX, v.x());
            maxX = Math.max(maxX, v.x());
        }
        float tableWidth = Math.max(1f, maxX - minX);

        List<Float> kept = new ArrayList<>();
        float minY = Collections.min(ys);
        float maxY = Collections.max(ys);

        for (float y : ys) {
            float coverage = mergedHorizontalCoverageAtY(component.horizontal, y);
            int intersections = countIntersectionsAtHorizontal(component.vertical, y);
            boolean isOuter = Math.abs(y - minY) <= SNAP_TOLERANCE || Math.abs(y - maxY) <= SNAP_TOLERANCE;
            boolean reliable = isOuter
                    || coverage >= tableWidth * MIN_BOUNDARY_COVERAGE_RATIO
                    || intersections >= MIN_BOUNDARY_INTERSECTIONS;
            if (reliable) {
                kept.add(y);
            }
        }
        return kept;
    }

    private boolean isBorderedGridCandidate(LineComponent component, List<Float> xs, List<Float> ys) {
        if (xs.size() < 3 || ys.size() < 3) {
            return false;
        }

        float minX = Collections.min(xs);
        float maxX = Collections.max(xs);
        float minY = Collections.min(ys);
        float maxY = Collections.max(ys);
        float tableWidth = Math.max(1f, maxX - minX);
        float tableHeight = Math.max(1f, maxY - minY);

        int internalVerticalRules = 0;
        for (float x : xs) {
            boolean isOuter = Math.abs(x - minX) <= SNAP_TOLERANCE || Math.abs(x - maxX) <= SNAP_TOLERANCE;
            if (isOuter) {
                continue;
            }
            float coverage = mergedVerticalCoverageAtX(component.vertical, x);
            int intersections = countIntersectionsAtVertical(component.horizontal, x);
            if (coverage >= tableHeight * GRID_INTERNAL_BOUNDARY_COVERAGE_RATIO
                    && intersections >= GRID_INTERNAL_BOUNDARY_MIN_INTERSECTIONS) {
                internalVerticalRules++;
            }
        }

        int internalHorizontalRules = 0;
        for (float y : ys) {
            boolean isOuter = Math.abs(y - minY) <= SNAP_TOLERANCE || Math.abs(y - maxY) <= SNAP_TOLERANCE;
            if (isOuter) {
                continue;
            }
            float coverage = mergedHorizontalCoverageAtY(component.horizontal, y);
            int intersections = countIntersectionsAtHorizontal(component.vertical, y);
            if (coverage >= tableWidth * GRID_INTERNAL_BOUNDARY_COVERAGE_RATIO
                    && intersections >= GRID_INTERNAL_BOUNDARY_MIN_INTERSECTIONS) {
                internalHorizontalRules++;
            }
        }

        return internalVerticalRules > 0 && internalHorizontalRules > 0;
    }

    private List<Float> selectReliableVerticalBoundaries(LineComponent component) {
        List<Float> xs = snapAndDeduplicate(
                component.vertical.stream().map(PageVectorLineExtractor.VerticalLine::x).collect(Collectors.toList()),
                SNAP_TOLERANCE
        );
        if (xs.size() <= 2) {
            return xs;
        }

        float minY = Float.MAX_VALUE;
        float maxY = Float.NEGATIVE_INFINITY;
        for (PageVectorLineExtractor.HorizontalLine h : component.horizontal) {
            minY = Math.min(minY, h.y());
            maxY = Math.max(maxY, h.y());
        }
        float tableHeight = Math.max(1f, maxY - minY);

        List<Float> kept = new ArrayList<>();
        float minX = Collections.min(xs);
        float maxX = Collections.max(xs);

        for (float x : xs) {
            float coverage = mergedVerticalCoverageAtX(component.vertical, x);
            int intersections = countIntersectionsAtVertical(component.horizontal, x);
            boolean isOuter = Math.abs(x - minX) <= SNAP_TOLERANCE || Math.abs(x - maxX) <= SNAP_TOLERANCE;
            boolean reliable = isOuter
                    || coverage >= tableHeight * MIN_BOUNDARY_COVERAGE_RATIO
                    || intersections >= MIN_BOUNDARY_INTERSECTIONS;
            if (reliable) {
                kept.add(x);
            }
        }
        return kept;
    }

    private float mergedHorizontalCoverageAtY(List<PageVectorLineExtractor.HorizontalLine> lines, float y) {
        List<float[]> ranges = new ArrayList<>();
        for (PageVectorLineExtractor.HorizontalLine line : lines) {
            if (Math.abs(line.y() - y) <= SNAP_TOLERANCE) {
                ranges.add(new float[]{line.x1(), line.x2()});
            }
        }
        return mergedRangeLength(ranges);
    }

    private float mergedVerticalCoverageAtX(List<PageVectorLineExtractor.VerticalLine> lines, float x) {
        List<float[]> ranges = new ArrayList<>();
        for (PageVectorLineExtractor.VerticalLine line : lines) {
            if (Math.abs(line.x() - x) <= SNAP_TOLERANCE) {
                ranges.add(new float[]{line.y1(), line.y2()});
            }
        }
        return mergedRangeLength(ranges);
    }

    private float mergedRangeLength(List<float[]> ranges) {
        if (ranges.isEmpty()) {
            return 0f;
        }
        ranges.sort(Comparator.comparingDouble(r -> r[0]));
        float total = 0f;
        float curStart = ranges.get(0)[0];
        float curEnd = ranges.get(0)[1];

        for (int i = 1; i < ranges.size(); i++) {
            float start = ranges.get(i)[0];
            float end = ranges.get(i)[1];
            if (start <= curEnd + LINE_JOIN_TOLERANCE) {
                curEnd = Math.max(curEnd, end);
            } else {
                total += Math.max(0f, curEnd - curStart);
                curStart = start;
                curEnd = end;
            }
        }
        total += Math.max(0f, curEnd - curStart);
        return total;
    }

    private int countIntersectionsAtHorizontal(List<PageVectorLineExtractor.VerticalLine> verticalLines, float y) {
        int count = 0;
        for (PageVectorLineExtractor.VerticalLine v : verticalLines) {
            if (y >= v.y1() - SNAP_TOLERANCE && y <= v.y2() + SNAP_TOLERANCE) {
                count++;
            }
        }
        return count;
    }

    private int countIntersectionsAtVertical(List<PageVectorLineExtractor.HorizontalLine> horizontalLines, float x) {
        int count = 0;
        for (PageVectorLineExtractor.HorizontalLine h : horizontalLines) {
            if (x >= h.x1() - SNAP_TOLERANCE && x <= h.x2() + SNAP_TOLERANCE) {
                count++;
            }
        }
        return count;
    }

    private static final class LineComponent {
        private final List<PageVectorLineExtractor.HorizontalLine> horizontal = new ArrayList<>();
        private final List<PageVectorLineExtractor.VerticalLine> vertical = new ArrayList<>();

        private float topY() {
            float max = Float.NEGATIVE_INFINITY;
            for (PageVectorLineExtractor.HorizontalLine h : horizontal) {
                max = Math.max(max, h.y());
            }
            return max;
        }
    }

    private record FrameRemovalResult(
            List<PageVectorLineExtractor.HorizontalLine> horizontal,
            List<PageVectorLineExtractor.VerticalLine> vertical
    ) {
    }

    private static final class UnionFind {
        private final int[] parent;
        private final int[] rank;

        private UnionFind(int size) {
            this.parent = new int[size];
            this.rank = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
                rank[i] = 0;
            }
        }

        private int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }

        private void union(int x, int y) {
            int rx = find(x);
            int ry = find(y);
            if (rx == ry) {
                return;
            }
            if (rank[rx] < rank[ry]) {
                parent[rx] = ry;
            } else if (rank[rx] > rank[ry]) {
                parent[ry] = rx;
            } else {
                parent[ry] = rx;
                rank[rx]++;
            }
        }
    }
}
