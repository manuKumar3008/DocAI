package com.bsg.ai.services.extraction.services.service.pdfExtraction;

import com.bsg.ai.services.extraction.services.records.ExtractionResult;
import com.bsg.ai.services.extraction.services.records.PageExtractionContext;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.components.DigitalTextExtractor;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.components.RotatedTextExtractor;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.components.ScannedPageExtractor;
import com.bsg.ai.services.ocr.service.LlmOcrService;
import com.bsg.ai.services.ocr.service.TesseractOcrService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extends PagePdfDocumentReader.
 * Order matters: each extractor sees soFar text from all previous extractors.
 */
@Slf4j
public class EnhancedPdfDocumentReader extends PagePdfDocumentReader implements AutoCloseable {

    private final List<PageContentExtractor> extractors;

    public EnhancedPdfDocumentReader(String resourceUrl, ChatClient chatClient,
                                      TesseractOcrService tesseractOcrService, LlmOcrService llmOcrService) {
        this((new DefaultResourceLoader()).getResource(resourceUrl), chatClient, tesseractOcrService, llmOcrService);
    }

    public EnhancedPdfDocumentReader(Resource resource, ChatClient chatClient,
                                      TesseractOcrService tesseractOcrService, LlmOcrService llmOcrService) {
        super(resource, PdfDocumentReaderConfig.defaultConfig());
        this.extractors = defaultExtractors(chatClient, tesseractOcrService, llmOcrService);
    }

    /**
     * Advanced constructor — supply your own extractor list.
     * Use this to add, remove, or reorder extractors without touching any other class.
     */
    public EnhancedPdfDocumentReader(Resource resource, PdfDocumentReaderConfig config, List<PageContentExtractor> extractors) {
        super(resource, config);
        this.extractors = List.copyOf(extractors);
    }

    private static List<PageContentExtractor> defaultExtractors(ChatClient chatClient,
                                                                  TesseractOcrService tesseractOcrService,
                                                                  LlmOcrService llmOcrService) {
        return List.of(
                new DigitalTextExtractor(tesseractOcrService, llmOcrService),
                new RotatedTextExtractor(),
//                new EmbeddedImageExtractor(chatClient),
                new ScannedPageExtractor(chatClient)
        );
    }

    @Override
    public List<Document> get() {
        List<Document> result = new ArrayList<>();
        PDPageTree pages = this.document.getDocumentCatalog().getPages();
        int totalPages   = pages.getCount();
        int pageNumber   = 1;

        for (PDPage page : pages) {
            log.info("Processing page {}/{}", pageNumber, totalPages);
            var document = processPage(page, pageNumber, totalPages);
             log.info("Extracted content: {}", document.getText());
            result.add(document);
            pageNumber++;
        }
        return result;
    }

    /**
     * Extract a single page by its 1-based page number.
     *
     * @param pageNumber 1-based page number
     * @return the extracted Document for that page
     * @throws IllegalArgumentException if pageNumber is out of range
     */
    public Document readPage(int pageNumber) {
        PDPageTree pages = this.document.getDocumentCatalog().getPages();
        int totalPages = pages.getCount();
        if (pageNumber < 1 || pageNumber > totalPages) {
            throw new IllegalArgumentException(
                    "Page number " + pageNumber + " is out of range [1, " + totalPages + "]");
        }
        log.info("Processing single page {}/{}", pageNumber, totalPages);
        return processPage(pages.get(pageNumber - 1), pageNumber, totalPages);
    }

    public int getDocumentTotalPages() {
        return this.document.getDocumentCatalog().getPages().getCount();
    }

    private Document processPage(PDPage page, int pageNumber, int totalPages) {
        PageExtractionContext ctx = new PageExtractionContext(this.document, page, pageNumber, totalPages, this.resourceFileName);

        StringBuilder combinedText = new StringBuilder();
        Map<String, Object> combinedMeta = new HashMap<>();

        for (PageContentExtractor extractor : extractors) {
            try {
                ExtractionResult result = extractor.extract(ctx, combinedText.toString());
                if (result.hasText()) {
                    if (!combinedText.isEmpty()) combinedText.append("\n");
                    combinedText.append(result.text());
                }
//                if (result.hasTables()) {
//                    // TODO: think where to put table data
//                    for (DetectedTable table : result.tables()) {
//                        combinedText.append("\n");
//                        combinedText.append(table.toMarkdown(0));
//                    }
//                }
                combinedMeta.putAll(result.metadata());
            } catch (Exception e) {
                log.warn("[{}] page {} failed: {}", extractor.name(), pageNumber, e.getMessage());
            }
        }

        return buildDocument(combinedText.toString(), pageNumber, combinedMeta);
    }

    private Document buildDocument(String text, int pageNumber, Map<String, Object> meta) {
        Document doc = new Document(StringUtils.hasText(text) ? text : "");
        doc.getMetadata().put(METADATA_START_PAGE_NUMBER, pageNumber);
        doc.getMetadata().put(METADATA_END_PAGE_NUMBER, pageNumber);
        if (resourceFileName != null) doc.getMetadata().put(METADATA_FILE_NAME, resourceFileName);
        doc.getMetadata().putAll(meta);
        return doc;
    }

    @Override
    public void close() {
        try {
            if (this.document != null) {
                this.document.close();
                log.debug("PDF Reader resources closed successfully.");
            }
        } catch (IOException e) {
            // Log the error - avoid throwing checked exceptions from close() if possible
            log.error("Error closing PDF Reader: {}", e.getMessage());
        }
    }
}
