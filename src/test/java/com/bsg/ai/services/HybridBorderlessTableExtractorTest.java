package com.bsg.ai.services;

import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.Cell;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.DetectedTable;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.services.BorderedTableExtractor;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.services.HybridBorderlessTableExtractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridBorderlessTableExtractorTest {

    @Test
    void extractsSummaryOfChargesWhenBorderedRegionsAreExcluded() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/extraction_test_docs/border_less_table_extraction_sample_invoice.pdf");
        HybridBorderlessTableExtractor extractor = new HybridBorderlessTableExtractor();
        BorderedTableExtractor borderedExtractor = new BorderedTableExtractor();

        try (PDDocument document = Loader.loadPDF(resource.getFile())) {
            List<DetectedTable> borderlessTables = extractBorderlessTables(document, extractor, borderedExtractor);

            boolean hasSummaryOfChargesRow = borderlessTables.stream()
                    .flatMap(table -> table.getRows().stream())
                    .flatMap(List::stream)
                    .map(Cell::getText)
                    .map(text -> text == null ? "" : text.toLowerCase(Locale.ROOT))
                    .map(String::toLowerCase)
                    .map(text -> text.replaceAll("\\s+", ""))
                    .anyMatch(text -> text.contains("onetimecharges"));

            boolean hasCustomerDetailsTable = borderlessTables.stream()
                    .flatMap(table -> table.getRows().stream())
                    .flatMap(List::stream)
                    .map(Cell::getText)
                    .map(text -> text == null ? "" : text.toLowerCase(Locale.ROOT))
                    .map(String::toLowerCase)
                    .map(text -> text.replaceAll("\\s+", ""))
                    .anyMatch(text -> text.contains("duedate"));

            assertTrue(hasSummaryOfChargesRow,
                    "Expected region-aware borderless extraction to include 'Summary of Charges' rows.");
            assertTrue(hasCustomerDetailsTable,
                    "Expected region-aware borderless extraction to include 'customer billing details' rows.");
        }
    }

    @Test
    void doesNotTreatGeneralInformationParagraphsAsBorderlessTables() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/extraction_test_docs/border_less_table_extraction_sample_invoice.pdf");
        HybridBorderlessTableExtractor extractor = new HybridBorderlessTableExtractor();
        BorderedTableExtractor borderedExtractor = new BorderedTableExtractor();

        try (PDDocument document = Loader.loadPDF(resource.getFile())) {
            List<DetectedTable> borderlessTables = extractBorderlessTables(document, extractor, borderedExtractor);
            borderlessTables.stream().map(DetectedTable::toMarkdown).forEach(System.out::println);

            boolean hasGeneralInfoParagraphRow = borderlessTables.stream()
                    .flatMap(table -> table.getRows().stream())
                    .flatMap(List::stream)
                    .map(Cell::getText)
                    .map(text -> text == null ? "" : text.toLowerCase(Locale.ROOT))
                    .anyMatch(text -> text.contains("in case of changes in any details")
                            || text.contains("general information"));

            assertFalse(hasGeneralInfoParagraphRow,
                    "Numbered paragraph content should not be extracted as borderless table.");
        }
    }

    @Test
    void assignsBorderlessTableIndicesAfterBorderedTables() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/extraction_test_docs/border_less_table_extraction_sample_invoice.pdf");
        HybridBorderlessTableExtractor extractor = new HybridBorderlessTableExtractor();
        BorderedTableExtractor borderedExtractor = new BorderedTableExtractor();

        try (PDDocument document = Loader.loadPDF(resource.getFile())) {
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                PDPage page = document.getPage(pageIndex);
                float pageHeight = page.getMediaBox().getHeight();
                float pageWidth = page.getMediaBox().getWidth();
                List<DetectedTable> borderedTables =
                        borderedExtractor.extractPageTables(page, pageIndex, pageWidth, pageHeight);
                int startIndex = borderedTables.size();

                List<DetectedTable> borderlessTables =
                        extractor.extractPageTables(document, pageIndex, borderedTables, startIndex);

                for (DetectedTable table : borderlessTables) {
                    assertTrue(table.getTableIndex() >= startIndex,
                            "Borderless table index should start after bordered table indices.");
                }
            }
        }
    }

    private List<DetectedTable> extractBorderlessTables(PDDocument document,
                                                        HybridBorderlessTableExtractor extractor,
                                                        BorderedTableExtractor borderedExtractor) throws IOException {
        List<DetectedTable> borderlessTables = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            PDPage page = document.getPage(pageIndex);
            float pageHeight = page.getMediaBox().getHeight();
            float pageWidth = page.getMediaBox().getWidth();
            List<DetectedTable> borderedTables =
                    borderedExtractor.extractPageTables(page, pageIndex, pageWidth, pageHeight);
            borderlessTables.addAll(
                    extractor.extractPageTables(document, pageIndex, borderedTables, borderedTables.size())
            );
        }
        borderlessTables.forEach(table -> System.out.println(table.toMarkdown()));
        return borderlessTables;
    }
}
