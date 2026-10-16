package com.bsg.ai.services.extraction.summary.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.bsg.ai.services.config.ApplicationPrompt;
import com.bsg.ai.services.ocr.service.TesseractOcrService;
import com.bsg.ai.services.extraction.summary.constant.Language;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.imgscalr.Scalr;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrSummaryService {

    private final ChatModel chatModel;
    private final ApplicationPrompt prompt;
    private final ObjectMapper mapper = new ObjectMapper();
    private final TesseractOcrService tessaractOcrService;

    public JsonNode processPdfWithOcr1(Resource resource, Language summaryLanguage) throws IOException, TesseractException {
        var targetJson = mapper.createArrayNode();

        try (PDDocument document = Loader.loadPDF(resource.getInputStream().readAllBytes())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300); // 300 DPI

                long ocrPageStartTime = System.currentTimeMillis();
                String text = tessaractOcrService.extractText(bim, null);
                System.out.println("Page " + (page + 1) + ": OCR Time taken is:" + (System.currentTimeMillis() - ocrPageStartTime) + " in millis");

                long llmPageStartTime = System.currentTimeMillis();
                var jsonNode = this.processResourceMedia(text, summaryLanguage);
                System.out.println("Page " + (page + 1) + ": LLM one page Time taken is:" + (System.currentTimeMillis() - llmPageStartTime) + " in millis");
                appendRecursively(targetJson, jsonNode, page + 1);
            }
        }
        return processAllSummaries(targetJson, summaryLanguage);
    }

    public JsonNode processPdfWithOcrInOneShot(Resource resource, Language summaryLanguage) throws IOException, TesseractException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata\\");
        tesseract.setLanguage("deu");
        tesseract.setOcrEngineMode(1);
        StringBuffer sb = new StringBuffer();
        try (PDDocument document = Loader.loadPDF(resource.getInputStream().readAllBytes())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300); // 300 DPI
                BufferedImage processed = preprocess(bim);

                long ocrPageStartTime = System.currentTimeMillis();
                String text = tesseract.doOCR(processed);
                System.out.println("Page " + (page + 1) + ": OCR Time taken is:" + (System.currentTimeMillis() - ocrPageStartTime) + " in millis");
                sb.append(text);
            }
        }
        return this.processResourceMedia(sb.toString(), summaryLanguage);
    }

    public JsonNode processPdfWithOcr(Resource resource, Language summaryLanguage) throws IOException, TesseractException {
        var targetJson = mapper.createArrayNode();

        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata\\");
        tesseract.setLanguage("deu");
        tesseract.setOcrEngineMode(1);

        try (PDDocument document = Loader.loadPDF(resource.getInputStream().readAllBytes())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300); // 300 DPI
                BufferedImage processed = preprocess(bim);

                long ocrPageStartTime = System.currentTimeMillis();
                String text = tesseract.doOCR(processed);
                System.out.println("Page " + (page + 1) + ": OCR Time taken is:" + (System.currentTimeMillis() - ocrPageStartTime) + " in millis");

                List<Media> resourceMedias = List.of(Media.builder()
                        .data(text)
                        .mimeType(MimeType.valueOf("text/plain"))
                        .build());
                long llmPageStartTime = System.currentTimeMillis();
                var jsonNode = this.processResourceMedia(text, summaryLanguage);
                System.out.println("Page " + (page + 1) + ": LLM one page Time taken is:" + (System.currentTimeMillis() - llmPageStartTime) + " in millis");
                appendRecursively(targetJson, jsonNode, page + 1);
            }
        }

        return processAllSummaries(targetJson, summaryLanguage);
    }

    /**
     * This method is used to prepare summery data page by page.
     */
    private void appendRecursively(ArrayNode jsonTo, JsonNode jsonFrom, int page) {
        var jsonNode = mapper.createObjectNode();
        jsonNode.put("page", page);
        jsonNode.set("summery", jsonFrom.get("summery"));
        jsonTo.add(jsonNode);
    }

    @SneakyThrows
  
private JsonNode processResourceMedia(String text, Language summaryLanguage) {

    var promptText = this.prompt.getDocSummaryPrompt();
    promptText = promptText.replace("{SUMMARY_LANGUAGE}", summaryLanguage.getDisplayName());

    Prompt docSummaryPrompt = Prompt.builder()
            .messages(
                    SystemMessage.builder()
                            .text(promptText)
                            .build(),

                    UserMessage.builder()
                            .text(text)
                            .build()
            )
            .build();

    log.info("Prompt: {}", docSummaryPrompt);

    var response = this.chatModel.call(docSummaryPrompt);

    if (response == null
            || response.getResult() == null
            || response.getResult().getOutput() == null) {
        throw new RuntimeException("Received null response from ChatModel");
    }

    String responseText = response.getResult().getOutput().getText();

    log.info("Received response from chat model: {}", responseText);

    return convertToJSONNode(responseText);
}

    private JsonNode convertToJSONNode(String text) {
        ObjectNode jsonNode = mapper.createObjectNode();
        jsonNode.put("summery", text);
        return jsonNode;
    }

  private JsonNode processAllSummaries(ArrayNode targetJson, Language summaryLanguage) {

    StringBuilder sb = new StringBuilder();

    targetJson.forEach(node -> {
        var summary = node.get("summery");
        if (summary != null) {
            sb.append(summary.asText()).append(" ");
        }
    });

    var promptText = this.prompt.getDocSummaryOfSummariesPrompt();
    promptText = promptText.replace("{SUMMARY_LANGUAGE}", summaryLanguage.getDisplayName());

    Prompt docSummaryOfSummariesPrompt = Prompt.builder()
            .messages(
                    SystemMessage.builder()
                            .text(promptText)
                            .build(),

                    UserMessage.builder()
                            .text("Page Summaries: " + sb)
                            .build()
            )
            .build();

    long llmAllSummaryStartTime = System.currentTimeMillis();

    log.info("Prompt summary of summaries: {}", docSummaryOfSummariesPrompt);

    var response = this.chatModel.call(docSummaryOfSummariesPrompt);

    if (response == null
            || response.getResult() == null
            || response.getResult().getOutput() == null) {
        throw new RuntimeException("Received null response from ChatModel");
    }

    String responseText = response.getResult().getOutput().getText();

    log.info(
            "Received response from chat model: {} LLM all summary time taken is: {} ms",
            responseText,
            System.currentTimeMillis() - llmAllSummaryStartTime
    );

    return convertToJSONNode(responseText);
}

    private BufferedImage preprocess(BufferedImage img) {
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
