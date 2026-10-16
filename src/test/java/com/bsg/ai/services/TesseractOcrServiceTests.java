package com.bsg.ai.services;

import com.bsg.ai.services.ocr.service.TesseractOcrService;
import lombok.SneakyThrows;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@SpringBootTest
class TesseractOcrServiceTests {

    static List<String> DOC_SUMMARY_TEST_PDF_PATHS = new ArrayList<>();

    static {
        DOC_SUMMARY_TEST_PDF_PATHS.add("static/summary/Daniel_Befristeter Traineevertrag_Daniel Zimmermann_VERTRAG_Befristeter Trainee Vertrag_01.01.2007.pdf");
    }

    @Autowired
    private TesseractOcrService tessaractOcrService;

    static Stream<String> getDocSummeryTestPDFPaths() {
        return DOC_SUMMARY_TEST_PDF_PATHS.stream();
    }

    @ParameterizedTest
    @MethodSource("getDocSummeryTestPDFPaths")
    @SneakyThrows
    void testTessaractOcrOutput(String path) throws Exception {
        var resource = new ClassPathResource(path);
        StringBuffer sb = new StringBuffer();
        try (PDDocument document = Loader.loadPDF(resource.getInputStream().readAllBytes())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300); // 300 DPI
                String extractedText = tessaractOcrService.extractText(bim, "deu");
                sb.append(extractedText);
            }
        }
        System.out.println("Extracted text is:" + sb.toString());
    }

}
