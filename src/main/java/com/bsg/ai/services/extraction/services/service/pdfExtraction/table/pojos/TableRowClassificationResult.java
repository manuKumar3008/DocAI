package com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos;

import java.util.ArrayList;
import java.util.List;

public class TableRowClassificationResult {

    public static class RowClassification {
        private final int rowIndex;
        private final TableRowRole role;
        private final double headerScore;
        private final double dataScore;

        public RowClassification(int rowIndex, TableRowRole role, double headerScore, double dataScore) {
            this.rowIndex = rowIndex;
            this.role = role;
            this.headerScore = headerScore;
            this.dataScore = dataScore;
        }

        public int getRowIndex() {
            return rowIndex;
        }

        public TableRowRole getRole() {
            return role;
        }

        public double getHeaderScore() {
            return headerScore;
        }

        public double getDataScore() {
            return dataScore;
        }
    }

    private final int headerRowCount;
    private final List<RowClassification> rows;

    public TableRowClassificationResult(int headerRowCount, List<RowClassification> rows) {
        this.headerRowCount = Math.max(0, headerRowCount);
        this.rows = rows == null ? new ArrayList<>() : rows;
    }

    public int getHeaderRowCount() {
        return headerRowCount;
    }

    public List<RowClassification> getRows() {
        return rows;
    }
}
