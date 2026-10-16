package com.bsg.ai.services.ocr.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
@Slf4j
public class LlmOcrService {

    private static final String NO_TEXT_SENTINEL = "[NO TEXT IN IMAGE]";

    private final ChatClient chatClient;

    public LlmOcrService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Extracts all visible text from the given image using the configured LLM vision model.
     *
     * @param image the image to OCR; must not be null
     * @return extracted text, or empty string if nothing was found or an error occurred
     */
    public String extractText(BufferedImage image) throws IOException {
        byte[] pngBytes = toPngBytes(image);

        Media media = new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(pngBytes));

        var systemMessage = SystemMessage.builder()
                .text("""
                        You are a universal OCR assistant capable of extracting text from any type of document image.
                        This includes, but is not limited to:
                          - Handwritten notes, letters, or forms
                          - Printed documents, reports, or articles
                          - Scanned PDFs or photographed pages
                          - Structured forms (checkboxes, fields, tables, signatures)
                          - Receipts, invoices, or financial documents
                          - Identity documents (passports, IDs, licenses)
                          - Medical records or prescriptions
                          - Legal contracts or certificates
                          - Charts, diagrams with text labels
                          - Mixed-content documents (printed + handwritten)
                          - Multi-language or multi-script documents
                          - Low-quality, skewed, or partially obscured images

                        Your rules:
                          1. Extract ALL visible text exactly as it appears — do not correct spelling, grammar, or formatting.
                          2. Do NOT translate any language — preserve the original script and language.
                          3. Do NOT infer or guess missing/illegible characters; use [illegible] as a placeholder.
                          4. Preserve spatial relationships: headings on their own line, tables as tab-separated rows.
                          5. For checkboxes or radio buttons, represent their state as [X] (checked) or [ ] (unchecked).
                          6. For signatures, stamps, or purely graphical elements with no readable text, use [SIGNATURE], [STAMP], or [GRAPHIC] respectively.
                          7. For redacted or blacked-out areas, use [REDACTED].
                          8. If a section is heavily degraded or unreadable, use [UNREADABLE SECTION].
                          9. Maintain reading order: top-to-bottom, left-to-right unless the document layout indicates otherwise.
                          10. CRITICAL — never guess ambiguous digits or characters, especially in dates, ID numbers, and
                              amounts. If even a single character in a date/number/ID could plausibly be read more than one
                              way, do not resolve it to a single "best guess" value. Output the unambiguous characters as-is
                              and mark each uncertain character individually, e.g. "21.07.[illegible][illegible]" rather than
                              silently completing it to "21.07.2023". A wrong-but-plausible-looking value is worse than an
                              honest [illegible] marker.
                          11. If the image contains no text at all, respond only with: %s
                        """.formatted(NO_TEXT_SENTINEL))
                .build();

        var userMessage = UserMessage.builder()
                .text("""
                        Extract ALL text visible in this image exactly as it appears.
                        Do not add any commentary, explanation, or formatting beyond what is described above.
                        """)
                .media(media)
                .build();

        long startTime = System.currentTimeMillis();
        try {
            ChatResponse chatResponse = chatClient.prompt()
                    .messages(systemMessage, userMessage)
                    .call()
                    .chatResponse();

            log.debug("LLM OCR completed in {} ms, model={}, usage={}",
                    System.currentTimeMillis() - startTime,
                    chatResponse != null
                            ? chatResponse.getMetadata().getModel() : "unknown",
                    chatResponse != null
                            ? chatResponse.getMetadata().getUsage() : "unknown");

            String content = chatResponse != null
                    ? chatResponse.getResult().getOutput().getText()
                    : null;

            if (!StringUtils.hasText(content) || content.contains(NO_TEXT_SENTINEL)) {
                return "";
            }
            return content.strip();

        } catch (Exception e) {
            log.error("LLM OCR call failed after {} ms: {}", System.currentTimeMillis() - startTime, e.getMessage());
            throw new IOException("LLM OCR failed: " + e.getMessage(), e);
        }
    }

    private byte[] toPngBytes(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", baos)) {
                throw new IOException("ImageIO could not encode image as PNG");
            }
            return baos.toByteArray();
        }
    }
}
