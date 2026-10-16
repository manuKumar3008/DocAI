package com.bsg.ai.services.util;

import com.bsg.ai.services.extraction.filter.dto.PdfData;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class PdfExtractor {

    public PdfData extract(Resource resource) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        StringBuilder textContent = new StringBuilder();
        int pages = 0;

        try (PDDocument document = Loader.loadPDF(resource.getInputStream().readAllBytes())) {
            pages = document.getNumberOfPages();
            for (int page = 0; page < pages; page++) {
                PDPage pdPage = document.getPage(page);
                PDResources resources = pdPage.getResources();

                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page + 1);
                stripper.setEndPage(page + 1);
                textContent.append(stripper.getText(document));

                for (COSName xObjectName : resources.getXObjectNames()) {
                    if (resources.isImageXObject(xObjectName)) {
                        PDImageXObject imageObject = (PDImageXObject) resources.getXObject(xObjectName);
                        images.add(imageObject.getImage());
                    }
                }
            }
        }

        return new PdfData(cleanText(textContent.toString()), images, pages);
    }

    public PdfData extract(Resource resource, Integer pageNo) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        String textContent = "";

        try (PDDocument document = Loader.loadPDF(resource.getInputStream().readAllBytes())) {
            int pageCount = document.getNumberOfPages();
            if (pageNo == null || pageNo < 0) {
                throw new IllegalArgumentException("Page no can not be equal or less than zero");
            }
            if (pageCount < pageNo) {
                throw new IllegalArgumentException("Page no exceeds total pages");
            }
            PDPage page = document.getPage(pageNo);
            PDResources resources = page.getResources();
            for (COSName xObjectName : resources.getXObjectNames()) {
                if (resources.isImageXObject(xObjectName)) {
                    PDImageXObject imageObject = (PDImageXObject) resources.getXObject(xObjectName);
                    images.add(imageObject.getImage());
                }
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageNo + 1);
            stripper.setEndPage(pageNo + 1);
            textContent = stripper.getText(document);
        }

        return new PdfData(cleanText(textContent), images, pageNo);
    }

    /**
     * Collapses runs of spaces/tabs to one space, trims each line, and collapses
     * multiple blank lines to one — but preserves line breaks. Line breaks carry
     * real structural signal on grid/form-style PDFs (which label goes with which
     * value); collapsing them (as a naive \s+ -> " " would) causes labels and
     * values to visually run together and lets blank cells silently shift
     * subsequent values out of alignment.
     */
    private String cleanText(String text) {
        return text
                .replaceAll("[ \\t]+", " ")
                .replaceAll("(?m)^\\s+|\\s+$", "")
                .replaceAll("\\n{2,}", "\n")
                .trim();
    }
}