package com.bsg.ai.services.extraction.services.service.pdfExtraction.table.services;

import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.Cell;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.DetectedTable;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.strippers.TextChunkCollector;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class has initial level code. for now we are not removing this code.
 * In near future this class may be removed
 * */
@Deprecated
class BorderlessTableExtractor {

    private static final float ROW_CLUSTER_TOLERANCE = 4.0f;
    private static final float BLOCK_GAP_TOLERANCE = 18.0f;
    private static final float COLUMN_CLUSTER_TOLERANCE = 8.0f;
    private static final int MIN_TABLE_COLUMNS = 2;
    private static final int MIN_BORDERLESS_TABLE_ROWS = 3;
    private static final float MIN_BORDERLESS_MULTI_COL_ROW_RATIO = 0.45f;
    private static final float MAX_BORDERLESS_LONG_TEXT_COL0_RATIO = 0.75f;
    private static final Pattern LIST_MARKER_PATTERN = Pattern.compile("^\\(?\\d+[\\).:-]?$");

    public List<DetectedTable> extractPageTables(PDDocument document, int pageIndex) throws IOException {
        TextChunkCollector collector = new TextChunkCollector();
        collector.setStartPage(pageIndex + 1);
        collector.setEndPage(pageIndex + 1);
        collector.getText(document);

        List<TextChunkCollector.TextChunk> chunks = collector.getChunks();
        if (chunks.isEmpty()) {
            return Collections.emptyList();
        }

        chunks.sort(Comparator.comparingDouble(TextChunkCollector.TextChunk::top));
        List<List<TextChunkCollector.TextChunk>> rows = clusterRows(chunks, ROW_CLUSTER_TOLERANCE);
        List<List<TextChunkCollector.TextChunk>> candidateRows = rows.stream()
                .filter(r -> r.size() >= 2)
                .collect(Collectors.toList());
        if (candidateRows.size() < 2) {
            return Collections.emptyList();
        }

        List<List<List<TextChunkCollector.TextChunk>>> rowBlocks = splitByVerticalGap(candidateRows, BLOCK_GAP_TOLERANCE);
        List<DetectedTable> tables = new ArrayList<>();
        int tableIndex = 0;
        for (List<List<TextChunkCollector.TextChunk>> block : rowBlocks) {
            if (block.size() < MIN_BORDERLESS_TABLE_ROWS) {
                continue;
            }
            if (isMultiColumnLayout(block)) continue;
            DetectedTable table = buildBorderlessTable(block, pageIndex, tableIndex);
            if (!table.getRows().isEmpty() && isLikelyBorderlessTable(table)) {
                tables.add(table);
                tableIndex++;
            }
        }
        return tables;
    }

    private DetectedTable buildBorderlessTable(List<List<TextChunkCollector.TextChunk>> rowBlock,
                                               int pageIndex, int tableIndex) {
        List<Float> anchors = new ArrayList<>();
        for (List<TextChunkCollector.TextChunk> row : rowBlock) {
            for (TextChunkCollector.TextChunk chunk : row) {
                anchors.add(chunk.left());
            }
        }
        List<Float> colAnchors = snapAndDeduplicate(anchors, COLUMN_CLUSTER_TOLERANCE);
        if (colAnchors.isEmpty()) {
            return new DetectedTable(pageIndex, tableIndex);
        }

        DetectedTable table = new DetectedTable(pageIndex, tableIndex);
        for (int r = 0; r < rowBlock.size(); r++) {
            List<TextChunkCollector.TextChunk> row = rowBlock.get(r);
            row.sort(Comparator.comparingDouble(TextChunkCollector.TextChunk::left));
            List<Cell> outputRow = new ArrayList<>();

            float rowTop = row.stream().map(TextChunkCollector.TextChunk::top).min(Float::compareTo).orElse(0f);
            float rowBottom = row.stream().map(TextChunkCollector.TextChunk::bottom).max(Float::compareTo).orElse(rowTop + 10f);

            for (int c = 0; c < colAnchors.size(); c++) {
                float left = colAnchors.get(c);
                float right = (c + 1 < colAnchors.size())
                        ? (colAnchors.get(c + 1) - 0.5f)
                        : row.stream().map(TextChunkCollector.TextChunk::right).max(Float::compareTo).orElse(left + 20f);

                int finalC = c;
                List<String> bucket = row.stream()
                        .filter(chunk -> nearestAnchorIndex(colAnchors, chunk.left()) == finalC)
                        .map(TextChunkCollector.TextChunk::text)
                        .collect(Collectors.toList());
                String text = String.join(" ", bucket).trim();

                Cell cell = new Cell(text, r, c, 1, 1, left, rowTop, Math.max(0.5f, right - left), Math.max(0.5f, rowBottom - rowTop));
                outputRow.add(cell);
            }
            table.addRow(outputRow);
        }
        return table;
    }

    private int nearestAnchorIndex(List<Float> anchors, float x) {
        int nearestIndex = 0;
        float nearestDistance = Math.abs(anchors.get(0) - x);
        for (int i = 1; i < anchors.size(); i++) {
            float distance = Math.abs(anchors.get(i) - x);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    private boolean isLikelyBorderlessTable(DetectedTable table) {
        if (table == null || table.getRows().size() < MIN_BORDERLESS_TABLE_ROWS) {
            return false;
        }
        if (!hasConsistentColumnStructure(table)) return false;
        if (looksLikeParagraphFlow(table)) return false;
        int totalRows = table.getRows().size();
        int rowsWithTwoOrMoreCols = 0;
        int listLikeRows = 0;
        int rowsWithOnlyCol0AndCol1 = 0;

        for (List<Cell> row : table.getRows()) {
            int nonEmptyCount = 0;
            boolean col0HasLongText = false;
            boolean col1HasText = false;
            boolean otherColsHaveText = false;
            boolean col0IsListMarker = false;

            for (Cell cell : row) {
                String text = normalizeCellText(cell.getText());
                if (text.isBlank()) {
                    continue;
                }
                nonEmptyCount++;
                if (cell.getColIndex() == 0) {
                    col0IsListMarker = LIST_MARKER_PATTERN.matcher(text).matches();
                    col0HasLongText = text.length() >= 24;
                } else if (cell.getColIndex() == 1) {
                    col1HasText = true;
                } else {
                    otherColsHaveText = true;
                }
            }

            if (nonEmptyCount >= 2) {
                rowsWithTwoOrMoreCols++;
            }
            if (col0IsListMarker && col1HasText && !otherColsHaveText) {
                listLikeRows++;
            }
            if (col0HasLongText && col1HasText && !otherColsHaveText) {
                rowsWithOnlyCol0AndCol1++;
            }
        }

        float multiColRatio = rowsWithTwoOrMoreCols / (float) totalRows;
        if (multiColRatio < MIN_BORDERLESS_MULTI_COL_ROW_RATIO) {
            return false;
        }

        float listLikeRatio = listLikeRows / (float) totalRows;
        if (listLikeRatio >= 0.60f) {
            return false;
        }

        float longCol0Ratio = rowsWithOnlyCol0AndCol1 / (float) totalRows;
        return longCol0Ratio < MAX_BORDERLESS_LONG_TEXT_COL0_RATIO;
    }

    private String normalizeCellText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ').trim();
    }

    private List<List<TextChunkCollector.TextChunk>> clusterRows(List<TextChunkCollector.TextChunk> chunks, float tolerance) {
        List<List<TextChunkCollector.TextChunk>> rows = new ArrayList<>();
        List<TextChunkCollector.TextChunk> current = new ArrayList<>();
        float currentTop = -1f;

        for (TextChunkCollector.TextChunk chunk : chunks) {
            if (currentTop < 0 || Math.abs(chunk.top() - currentTop) <= tolerance) {
                current.add(chunk);
                if (currentTop < 0) {
                    currentTop = chunk.top();
                }
            } else {
                current.sort(Comparator.comparingDouble(TextChunkCollector.TextChunk::left));
                rows.add(new ArrayList<>(current));
                current.clear();
                current.add(chunk);
                currentTop = chunk.top();
            }
        }
        if (!current.isEmpty()) {
            current.sort(Comparator.comparingDouble(TextChunkCollector.TextChunk::left));
            rows.add(current);
        }
        return rows;
    }

    private List<List<List<TextChunkCollector.TextChunk>>> splitByVerticalGap(
            List<List<TextChunkCollector.TextChunk>> rows, float tolerance) {
        List<List<List<TextChunkCollector.TextChunk>>> blocks = new ArrayList<>();
        List<List<TextChunkCollector.TextChunk>> current = new ArrayList<>();
        float lastBottom = -1f;

        for (List<TextChunkCollector.TextChunk> row : rows) {
            float rowTop = row.stream().map(TextChunkCollector.TextChunk::top).min(Float::compareTo).orElse(0f);
            float rowBottom = row.stream().map(TextChunkCollector.TextChunk::bottom).max(Float::compareTo).orElse(rowTop);

            if (lastBottom >= 0 && rowTop - lastBottom > tolerance && !current.isEmpty()) {
                blocks.add(new ArrayList<>(current));
                current.clear();
            }
            current.add(row);
            lastBottom = rowBottom;
        }
        if (!current.isEmpty()) {
            blocks.add(current);
        }
        return blocks;
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

    private boolean isMultiColumnLayout(List<List<TextChunkCollector.TextChunk>> rows) {
        List<Float> leftEdges = new ArrayList<>();

        for (List<TextChunkCollector.TextChunk> row : rows) {
            for (TextChunkCollector.TextChunk chunk : row) {
                leftEdges.add(chunk.left());
            }
        }

        List<Float> clusters = snapAndDeduplicate(leftEdges, COLUMN_CLUSTER_TOLERANCE);

        // Multi-column pages usually have 2–3 strong vertical bands
        if (clusters.size() <= 3) {
            // Now check vertical continuity
            int consistentRows = 0;

            for (List<TextChunkCollector.TextChunk> row : rows) {
                int aligned = 0;
                for (TextChunkCollector.TextChunk chunk : row) {
                    int idx = nearestAnchorIndex(clusters, chunk.left());
                    if (Math.abs(chunk.left() - clusters.get(idx)) < 3.0f) {
                        aligned++;
                    }
                }
                if (aligned >= row.size() - 1) {
                    consistentRows++;
                }
            }

            float ratio = consistentRows / (float) rows.size();

            // High consistency = multi-column text, not table
            return ratio > 0.8f;
        }

        return false;
    }

    private boolean hasConsistentColumnStructure(DetectedTable table) {
        int colCount = table.getRows().get(0).size();
        int consistentRows = 0;

        for (List<Cell> row : table.getRows()) {
            int filledCols = 0;
            for (Cell cell : row) {
                if (!normalizeCellText(cell.getText()).isBlank()) {
                    filledCols++;
                }
            }

            if (filledCols >= colCount - 1) {
                consistentRows++;
            }
        }

        return (consistentRows / (float) table.getRows().size()) > 0.6f;
    }

    private boolean looksLikeParagraphFlow(DetectedTable table) {
        int longCells = 0;
        int totalCells = 0;

        for (List<Cell> row : table.getRows()) {
            for (Cell cell : row) {
                String text = normalizeCellText(cell.getText());
                if (!text.isBlank()) {
                    totalCells++;
                    if (text.length() > 40) {
                        longCells++;
                    }
                }
            }
        }

        return (longCells / (float) totalCells) > 0.5f;
    }
}
