package com.bsg.ai.services.ocr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class TesseractOcrService {

    @Value("${system.ocr.tessaract.characterdatapath}")
    private String characterDataPath;

    @Value("${system.ocr.tessaract.enginemode:1}")
    private int ocrEngineMode;

    @Value("${system.ocr.tessaract.contentlang:eng}")
    private String language;

    public String extractText(BufferedImage bufferedImage, String lang) throws IOException, TesseractException {
        String ocrLang = lang != null ? lang : language;
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(characterDataPath);
        tesseract.setLanguage(ocrLang);
        tesseract.setOcrEngineMode(ocrEngineMode);
        log.debug("OCR language is:{}", ocrLang);

        long ocrPageStartTime = System.currentTimeMillis();
        String text = tesseract.doOCR(preprocessBufferedImage(bufferedImage));
        log.debug("OCR Time taken is: {}  in millis", (System.currentTimeMillis() - ocrPageStartTime));
        return text;
    }

    private BufferedImage preprocessBufferedImage(BufferedImage img) {
        // Target width ~2000px (good enough for OCR)
        int targetWidth = 2000;

        if (img.getWidth() > targetWidth) {
            img = Scalr.resize(img, targetWidth);
        }

        // Convert to grayscale (faster for OCR)
        BufferedImage gray = new BufferedImage(
                img.getWidth(), img.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );
        gray.getGraphics().drawImage(img, 0, 0, null);
        return gray;
    }

}
