package com.bsg.ai.services.util;

import com.bsg.ai.services.extraction.filter.dto.PdfData;
import com.bsg.ai.services.ocr.service.LlmOcrService;
import com.bsg.ai.services.ocr.service.TesseractOcrService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceTextExtractionService {

    private final PdfExtractor pdfExtractor;
    private final TesseractOcrService tessaractOcrService;
    private final LlmOcrService llmOcrService;

    @Cacheable(value = "extractTextFromResource", key = "#docId + #resource.filename", cacheManager = "cacheManagerOneDay")
    public String extractTextFromResource(Resource resource, String docId) {
        return processResource(resource, null);
    }

    @Cacheable(value = "extractTextFromResource", key = "#docId + #resource.filename + #pageNo", cacheManager = "cacheManagerOneDay")
    public String extractTextFromResource(Resource resource, String docId, Integer pageNo) {
        return processResource(resource, pageNo);
    }

    @SneakyThrows
    private String processResource(Resource resource, Integer pageNo) {
        String filename = resource.getFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Resource must have a filename");
        }
        String extension = getFileExtension(filename).toLowerCase();
        StringBuffer pageText = new StringBuffer();
        switch (extension) {
            case "pdf" -> {
                PdfData pdfData = (pageNo == null)
                        ? pdfExtractor.extract(resource)
                        : pdfExtractor.extract(resource, pageNo);
                pageText.append(pdfData.getContent());
                for (BufferedImage bufferedImage : pdfData.getImages()) {
                    pageText.append(performOcr(bufferedImage));   // <-- was tessaractOcrService.extractText(...)
                }
            }
            case "png", "jpg", "jpeg" -> {
                BufferedImage image = ImageIO.read(resource.getInputStream());
                if (image != null) {
                    pageText.append(performOcr(image));   // <-- changed
                }
            }
            case "tif", "tiff" -> {
                ImageReader reader = ImageIO.getImageReadersByFormatName("tiff").next();
                try (ImageInputStream stream = ImageIO.createImageInputStream(resource.getInputStream())) {
                    reader.setInput(stream);
                    int numPages = reader.getNumImages(true);

                    if (pageNo == null) {
                        for (int i = 0; i < numPages; i++) {
                            BufferedImage pageImage = reader.read(i);
                            pageText.append(performOcr(pageImage));   // <-- changed
                        }
                    } else {
                        if (pageNo < 0 || pageNo >= numPages) {
                            throw new IllegalArgumentException("Invalid pageNo for TIFF: " + pageNo);
                        }
                        BufferedImage pageImage = reader.read(pageNo);
                        pageText.append(performOcr(pageImage));   // <-- changed
                    }
                } finally {
                    reader.dispose();
                }
            }
            default -> throw new UnsupportedOperationException("Unsupported file type: " + extension);
        }
        return pageText.toString();
    }

    /**
     * Tries LLM vision OCR first (best quality on scanned/handwritten/checkbox content);
     * falls back to Tesseract if the LLM call fails or returns nothing.
     * Mirrors DigitalTextExtractor's fallback order so both pipelines behave consistently.
     */
    private String performOcr(BufferedImage image) {
        try {
            String llmText = llmOcrService.extractText(image);
            if (StringUtils.hasText(llmText)) {
                return llmText;
            }
        } catch (Exception e) {
            log.warn("LLM OCR failed ({}), falling back to Tesseract", e.getMessage());
        }

        try {
            String tesseractText = tessaractOcrService.extractText(image, null);
            return StringUtils.hasText(tesseractText) ? tesseractText.strip() : "";
        } catch (Exception e) {
            log.error("Tesseract OCR also failed: {}", e.getMessage());
            return "";
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1);
    }
}