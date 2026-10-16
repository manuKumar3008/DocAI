package com.bsg.ai.services.extraction.services.service.pdfExtraction.table.services;

import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.Cell;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.TableRowRole;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.DetectedTable;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.TableRowClassificationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Heuristic classifier for HEADER vs DATA rows.
 * Designed for tables extracted from visible-cell boundaries.
 */
public class TableRowClassifier {

    private static final Pattern NUMERIC_TOKEN = Pattern.compile("[-+]?\\d+(?:[.,]\\d+)?(?:%|sec|secs|s|ms|n=\\d+)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALPHA_TOKEN = Pattern.compile(".*[A-Za-z].*");

    public TableRowClassificationResult classify(DetectedTable table) {
        if (table == null || table.getRows().isEmpty()) {
            return new TableRowClassificationResult(0, new ArrayList<>());
        }

        List<TableRowClassificationResult.RowClassification> classifications = new ArrayList<>();
        int totalCols = maxColumnCount(table);

        List<RowStats> rowStats = new ArrayList<>();
        for (int r = 0; r < table.getRows().size(); r++) {
            List<Cell> row = table.getRows().get(r);
            RowStats stats = analyzeRow(row, totalCols);
            rowStats.add(stats);
            double headerScore = scoreHeader(stats, r);
            double dataScore = scoreData(stats, r);

            TableRowRole role;
            if (Math.abs(headerScore - dataScore) < 0.75) {
                role = TableRowRole.UNKNOWN;
            } else {
                role = headerScore > dataScore ? TableRowRole.HEADER : TableRowRole.DATA;
            }
            classifications.add(new TableRowClassificationResult.RowClassification(r, role, headerScore, dataScore));
        }

        // Header rows are expected to be contiguous at the top.
        int headerRowCount = 0;
        for (int i = 0; i < classifications.size(); i++) {
            TableRowClassificationResult.RowClassification rc = classifications.get(i);
            if (rc.getRole() == TableRowRole.HEADER || (i == 0 && rc.getRole() == TableRowRole.UNKNOWN)) {
                headerRowCount++;
            } else {
                break;
            }
        }

        // Promote top structural bands to header until first strong body row.
        int firstStrongDataRow = findFirstStrongDataRow(classifications, rowStats);
        if (firstStrongDataRow > 0) {
            headerRowCount = Math.max(headerRowCount, firstStrongDataRow);
        }

        // Finalize roles based on top-contiguous header split.
        List<TableRowClassificationResult.RowClassification> finalRows = new ArrayList<>();
        for (TableRowClassificationResult.RowClassification rc : classifications) {
            TableRowRole role;
            if (rc.getRowIndex() < headerRowCount) {
                role = TableRowRole.HEADER;
            } else {
                role = TableRowRole.DATA;
            }
            finalRows.add(new TableRowClassificationResult.RowClassification(
                    rc.getRowIndex(), role, rc.getHeaderScore(), rc.getDataScore()
            ));
        }

        return new TableRowClassificationResult(headerRowCount, finalRows);
    }

    private int findFirstStrongDataRow(List<TableRowClassificationResult.RowClassification> rows, List<RowStats> stats) {
        for (int i = 0; i < rows.size(); i++) {
            TableRowClassificationResult.RowClassification rc = rows.get(i);
            RowStats rs = stats.get(i);
            boolean strongNumeric = rs.numericRatio >= 0.25 && rs.nonEmptyCells >= 3;
            boolean noSpan = rs.spanCells == 0;
            boolean scorePrefersData = rc.getDataScore() - rc.getHeaderScore() >= 1.25;
            if (strongNumeric && noSpan && scorePrefersData) {
                return i;
            }
        }
        return -1;
    }

    private int maxColumnCount(DetectedTable table) {
        int max = 0;
        for (List<Cell> row : table.getRows()) {
            for (Cell cell : row) {
                max = Math.max(max, cell.getColIndex() + Math.max(1, cell.getColSpan()));
            }
        }
        return Math.max(1, max);
    }

    private RowStats analyzeRow(List<Cell> row, int totalCols) {
        int nonEmpty = 0;
        int spanCells = 0;
        int numericTokens = 0;
        int alphaTokens = 0;
        int tokenCount = 0;
        int occupiedCols = 0;

        for (Cell cell : row) {
            if (cell == null) {
                continue;
            }
            occupiedCols += Math.max(1, cell.getColSpan());
            if (cell.getRowSpan() > 1 || cell.getColSpan() > 1) {
                spanCells++;
            }

            String text = cell.getText() == null ? "" : cell.getText().trim();
            if (text.isEmpty()) {
                continue;
            }
            nonEmpty++;

            String[] tokens = text.split("\\s+");
            for (String token : tokens) {
                if (token.isBlank()) {
                    continue;
                }
                tokenCount++;
                if (NUMERIC_TOKEN.matcher(token).matches()) {
                    numericTokens++;
                }
                if (ALPHA_TOKEN.matcher(token).matches()) {
                    alphaTokens++;
                }
            }
        }

        RowStats stats = new RowStats();
        stats.nonEmptyCells = nonEmpty;
        stats.spanCells = spanCells;
        stats.tokenCount = tokenCount;
        stats.numericRatio = tokenCount == 0 ? 0d : (double) numericTokens / tokenCount;
        stats.alphaRatio = tokenCount == 0 ? 0d : (double) alphaTokens / tokenCount;
        stats.coverageRatio = totalCols == 0 ? 0d : (double) occupiedCols / totalCols;
        return stats;
    }

    private double scoreHeader(RowStats stats, int rowIndex) {
        double score = 0d;
        if (rowIndex <= 1) {
            score += 1.5;
        }
        if (stats.spanCells > 0) {
            score += 2.0;
        }
        if (stats.nonEmptyCells <= 2) {
            score += 0.5;
        }
        if (stats.alphaRatio >= 0.50) {
            score += 1.0;
        }
        if (stats.numericRatio <= 0.30) {
            score += 1.0;
        }
        if (stats.coverageRatio < 0.70) {
            score += 0.7;
        }
        return score;
    }

    private double scoreData(RowStats stats, int rowIndex) {
        double score = 0d;
        if (rowIndex >= 1) {
            score += 0.5;
        }
        if (stats.spanCells == 0) {
            score += 1.5;
        }
        if (stats.nonEmptyCells >= 3) {
            score += 1.0;
        }
        if (stats.numericRatio >= 0.35) {
            score += 1.8;
        }
        if (stats.coverageRatio >= 0.80) {
            score += 1.0;
        }
        return score;
    }

    private static class RowStats {
        int nonEmptyCells;
        int spanCells;
        int tokenCount;
        double numericRatio;
        double alphaRatio;
        double coverageRatio;
    }
}
