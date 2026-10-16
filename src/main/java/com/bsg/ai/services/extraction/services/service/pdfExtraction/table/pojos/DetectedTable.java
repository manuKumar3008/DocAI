package com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
public class DetectedTable {
    private final int pageIndex;
    private final int tableIndex;
    private final List<List<Cell>> rows = new ArrayList<>();

    public DetectedTable(int pageIndex, int tableIndex) {
        this.pageIndex = pageIndex;
        this.tableIndex = tableIndex;
    }

    public void addRow(List<Cell> row) {
        rows.add(row);
    }

    public String toMarkdown() {
        return toMarkdown(0);
    }

    /**
     * @param headerRowCount number of top rows to merge into markdown headers.
     *                       Use 0 when header rows are unknown.
     */
    public String toMarkdown(int headerRowCount) {
        if (rows.isEmpty()) {
            return "";
        }

        int rowCount = rows.size();
        int columnCount = rows.stream()
                .mapToInt(this::resolveColumnCount)
                .max()
                .orElse(0);
        if (columnCount <= 0) {
            return "";
        }

        String[][] grid = buildGrid(rowCount, columnCount);
        int safeHeaderRows = Math.max(0, Math.min(headerRowCount, rowCount));
        List<String> header = safeHeaderRows > 0
                ? buildMergedHeader(grid, safeHeaderRows, columnCount)
                : new ArrayList<>();

        StringBuilder md = new StringBuilder();
        if (!header.isEmpty()) {
            md.append(toMarkdownLine(header)).append('\n');
            md.append(toMarkdownLine(separatorRow(columnCount)));
        }

        for (int r = safeHeaderRows; r < rowCount; r++) {
            String markdownLine = toMarkdownLine(rowValues(grid[r], columnCount));
            if (StringUtils.isNotBlank(markdownLine)) {
                md.append('\n').append(markdownLine);
            }
        }
        return md.toString();
    }

    public void printMarkdown() {
        System.out.println(toMarkdown(0));
    }

    public void printMarkdown(int headerRowCount) {
        System.out.println(toMarkdown(headerRowCount));
    }

    private int resolveColumnCount(List<Cell> row) {
        return row.stream()
                .mapToInt(cell -> cell.getColIndex() + Math.max(1, cell.getColSpan()))
                .max()
                .orElse(0);
    }

    private String[][] buildGrid(int rowCount, int columnCount) {
        String[][] grid = new String[rowCount][columnCount];
        for (int r = 0; r < rows.size(); r++) {
            List<Cell> ordered = rows.get(r).stream()
                    .sorted(Comparator.comparingInt(Cell::getColIndex))
                    .toList();
            for (Cell cell : ordered) {
                int start = Math.max(0, cell.getColIndex());
                int span = Math.max(1, cell.getColSpan());
                if (start >= columnCount) {
                    continue;
                }
                String text = sanitize(cell.getText());
                for (int c = start; c < Math.min(columnCount, start + span); c++) {
                    // Markdown has no colspan. Repeat merged text in covered columns.
                    grid[r][c] = text;
                }
            }
        }
        return grid;
    }

    private List<String> buildMergedHeader(String[][] grid, int headerRowCount, int columnCount) {
        List<String> header = new ArrayList<>();
        for (int c = 0; c < columnCount; c++) {
            List<String> parts = new ArrayList<>();
            for (int r = 0; r < headerRowCount; r++) {
                String value = grid[r][c];
                if (value != null && !value.isBlank() && !parts.contains(value)) {
                    parts.add(value);
                }
            }
            header.add(parts.isEmpty() ? ("Col " + (c + 1)) : String.join(" / ", parts));
        }
        return header;
    }

    private List<String> syntheticHeader(int columnCount) {
        List<String> header = new ArrayList<>();
        for (int c = 0; c < columnCount; c++) {
            header.add("Col " + (c + 1));
        }
        return header;
    }

    private List<String> rowValues(String[] row, int columnCount) {
        List<String> values = new ArrayList<>();
        for (int c = 0; c < columnCount; c++) {
            String value = row[c];
            values.add(value == null ? "" : value);
        }
        return values;
    }

    private List<String> separatorRow(int columnCount) {
        List<String> separator = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            separator.add("---");
        }
        return separator;
    }

    private String toMarkdownLine(List<String> columns) {
        if (columns.stream().anyMatch(column -> !column.isBlank())) {
            return "| " + String.join(" | ", columns) + " |";
        } else {
            return "";
        }
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("|", "\\|")
                .replace("\r\n", "<br>")
                .replace("\n", "<br>")
                .replace('\r', ' ')
                .trim();
    }
}
