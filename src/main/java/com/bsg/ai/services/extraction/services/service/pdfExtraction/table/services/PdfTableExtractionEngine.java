package com.bsg.ai.services.extraction.services.service.pdfExtraction.table.services;

import com.bsg.ai.services.extraction.services.records.PageExtractionContext;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.Cell;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.DetectedTable;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.PageLayout;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.TableRowClassificationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates table extraction and delegates implementation details
 * to focused extractors/analyzers.
 */
public class PdfTableExtractionEngine {

    private final TableRowClassifier rowClassifier;
    private final ObjectMapper objectMapper;
    private final BorderedTableExtractor borderedExtractor;
    private final HybridBorderlessTableExtractor borderlessExtractor;
    private final PageLayoutAnalyzer pageLayoutAnalyzer;
    private final boolean borderlessFallbackEnabled;

    public PdfTableExtractionEngine() {
        this(false);
    }

    public PdfTableExtractionEngine(boolean borderlessFallbackEnabled) {
        this.borderlessFallbackEnabled = borderlessFallbackEnabled;
        this.rowClassifier = new TableRowClassifier();
        this.objectMapper = new ObjectMapper();
        this.borderlessExtractor = new HybridBorderlessTableExtractor();
        this.borderedExtractor = new BorderedTableExtractor();
        this.pageLayoutAnalyzer = new PageLayoutAnalyzer();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    public List<DetectedTable> extractDocumentTables(PDDocument document) throws IOException {
        List<DetectedTable> tables = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            tables.addAll(extractPageTables(document, pageIndex));
        }
        return tables;
    }

    public List<List<List<Cell>>> extractPageCellGrids(PDDocument document, int pageIndex) throws IOException {
        return extractPageTables(document, pageIndex).stream()
                .map(DetectedTable::getRows)
                .collect(Collectors.toList());
    }

    public List<DetectedTable> extractPageTables(PDDocument document, int pageIndex) throws IOException {
        PDPage page = document.getPage(pageIndex);
        float pageHeight = page.getMediaBox().getHeight();
        float pageWidth = page.getMediaBox().getWidth();

        List<DetectedTable> lineTables = borderedExtractor.extractPageTables(page, pageIndex, pageWidth, pageHeight);
        if (!borderlessFallbackEnabled) {
            return lineTables;
        }

        List<DetectedTable> borderlessTables =
                borderlessExtractor.extractPageTables(document, pageIndex, lineTables, lineTables.size());

        if (lineTables.isEmpty()) {
            return borderlessTables;
        }
        if (borderlessTables.isEmpty()) {
            return lineTables;
        }

        List<DetectedTable> combined = new ArrayList<>(lineTables.size() + borderlessTables.size());
        combined.addAll(lineTables);
        combined.addAll(borderlessTables);
        return combined;
    }

    public TableRowClassificationResult classifyRows(DetectedTable table) {
        return rowClassifier.classify(table);
    }

    public String tablesToJson(List<DetectedTable> tables) throws JsonProcessingException {
        return objectMapper.writeValueAsString(tables == null ? Collections.emptyList() : tables);
    }

    public String tablesToPrettyJson(List<DetectedTable> tables) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(tables == null ? Collections.emptyList() : tables);
    }

    public PageLayout identifyPageLayout(PageExtractionContext ctx) throws IOException {
        return pageLayoutAnalyzer.identifyPageLayout(ctx);
    }
}
