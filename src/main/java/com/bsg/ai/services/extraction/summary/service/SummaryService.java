package com.bsg.ai.services.extraction.summary.service;

import com.bsg.ai.services.config.ApplicationPrompt;
import com.bsg.ai.services.extraction.filter.service.ExternalAPIService;
import com.bsg.ai.services.extraction.summary.constant.Language;
import com.bsg.ai.services.extraction.summary.constant.SummaryType;
import com.bsg.ai.services.extraction.summary.controller.SummaryController.AIChatDto;
import com.bsg.ai.services.ocr.service.TesseractOcrService;
import com.bsg.ai.services.util.PdfExtractor;
import com.bsg.ai.services.util.ResourceTextExtractionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SummaryService {

    private final ChatModel chatModel;
    private final ApplicationPrompt prompt;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExternalAPIService externalAPIService;
    private final ResourceTextExtractionService resourceTextExtractionService;
    private final TesseractOcrService tessaractOcrService;
    private final PdfExtractor pdfExtractor;

    @Value("${app.storage.base-dir}")
    private String baseDir;

    @Value("${system.doc.summary.max.page:1}")
    private Integer maxPage;

    @SneakyThrows
    public JsonNode processResourceFromStorage(
            String docId,
            SummaryType summaryType,
            Language summaryLanguage,
            String storagePath) {

        File file = resolveStorageFile(storagePath);
        Resource resource = new FileSystemResource(file);

        if (!file.exists() || !file.isFile()) {
            throw new RuntimeException("File not found for summary: " + file.getAbsolutePath());
        }

        return this.processResourceWithOcr(resource, summaryType, docId, summaryLanguage);
    }

    private File resolveStorageFile(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("storagePath is required");
        }

        String normalizedInput = storagePath.trim().replace("\\", "/");
        Path candidate = Paths.get(normalizedInput);

        Path resolved = candidate.isAbsolute()
                ? candidate.normalize()
                : Paths.get(baseDir).toAbsolutePath().normalize().resolve(normalizedInput).normalize();

        log.info("Resolved storage path. baseDir={}, input={}, resolved={}", baseDir, storagePath, resolved);
        return resolved.toFile();
    }

    @SneakyThrows
    public JsonNode processResource(String docId, SummaryType summaryType, Language summaryLanguage) {
        var file = externalAPIService.getFileResource(docId);
        return this.processResourceWithOcr(file, summaryType, docId, summaryLanguage);
    }

    public JsonNode processResource(File file, SummaryType summaryType) {
        var targetJson = mapper.createArrayNode();
        callPDFPageByPage(file, null, null, targetJson, summaryType);
        return processAllSummaries(targetJson);
    }

    public JsonNode processResourceWithOcr(Resource resource, SummaryType summaryType, String docId, Language summaryLanguage) {
        return processResourceText(resourceTextExtractionService.extractTextFromResource(resource, docId), summaryType, summaryLanguage);
    }

    private JsonNode processAllSummaries(ArrayNode targetJson) {
        StringBuilder sb = new StringBuilder();
        targetJson.forEach(node -> {
            var summary = node.get("summary");
            if (summary != null) {
                sb.append(summary.asText()).append(" ");
            }
        });

        var promptText = this.prompt.getDocSummaryOfSummariesPrompt();
        var docSummaryOfSummariesPrompt = Prompt.builder().messages(
                SystemMessage.builder().text(promptText).build(),
                UserMessage.builder().text("Page Summaries: " + sb).build()
        ).build();

        log.info("Prompt summary of summaries: {}", docSummaryOfSummariesPrompt);
        var response = this.chatModel.call(docSummaryOfSummariesPrompt);
        log.info("Received response from chat model: {}", response.getResult().getOutput().getText());

        return convertToJSONNode(response.getResult().getOutput().getText(), "summary");
    }

    private void callPDFPageByPage(File file, Integer fromPage, Integer noOfPages, ArrayNode targetJson, SummaryType summaryType) {
        fromPage = fromPage == null ? 1 : fromPage;
        noOfPages = noOfPages == null ? maxPage : noOfPages;

        try {
            var imageResources = convertPDFToImage(file, fromPage, noOfPages);
            if (!imageResources.isEmpty()) {
                List<Media> resourceMedias = imageResources.stream()
                        .map(imageResource -> Media.builder()
                                .data(imageResource)
                                .mimeType(MimeType.valueOf(String.valueOf(MediaType.IMAGE_PNG)))
                                .build())
                        .toList();

                var jsonNode = this.processResourceMedia(resourceMedias, summaryType);
                appendRecursively(targetJson, jsonNode, fromPage, fromPage + Math.min(noOfPages, imageResources.size()) - 1);

                if (jsonNode != null) {
                    log.info("Response has a NULL value: {} from Page: {} to Page: {}", jsonNode, fromPage, fromPage + noOfPages);
                    callPDFPageByPage(file, fromPage + noOfPages, noOfPages, targetJson, summaryType);
                }
            }
        } catch (IllegalArgumentException ie) {
            log.info("Page end reached from Page: {} to Page: {}", fromPage, fromPage + noOfPages);
        }
    }

    private void appendRecursively(ArrayNode jsonTo, JsonNode jsonFrom, int fromPage, int toPage) {
        var jsonNode = mapper.createObjectNode();
        jsonNode.put("page", generatePageNumberString(fromPage, toPage));
        jsonNode.set("summary", jsonFrom.get("summary"));
        jsonTo.add(jsonNode);
    }

    private String generatePageNumberString(int fromPage, int toPage) {
        if (fromPage == toPage) {
            return String.valueOf(fromPage);
        } else if (fromPage == toPage - 1) {
            return String.format("%d, %d", fromPage, toPage);
        } else {
            return String.format("%d to %d", fromPage, toPage);
        }
    }

    private JsonNode convertToJSONNode(String text, String field) {
        text = cleanModelOutput(text);
        ObjectNode jsonNode = mapper.createObjectNode();
        jsonNode.put(field, text != null ? text : "");
        return jsonNode;
    }

    private String cleanModelOutput(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("```html", "")
                .replace("```json", "")
                .replace("```", "")
                .strip();
    }

    private boolean checkResponseHasAnyNull(JsonNode jsonTree) {
        Iterator<Map.Entry<String, JsonNode>> fields = jsonTree.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue().isValueNode()) {
                if (field.getValue().isNull()
                        || (field.getValue().isTextual() && field.getValue().asText().isBlank())
                        || (field.getValue().isTextual() && field.getValue().asText().equals("Not specified"))) {
                    return true;
                }
            } else if (field.getValue().isContainerNode()) {
                if (checkResponseHasAnyNull(field.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    @SneakyThrows
    private JsonNode processResourceMedia(List<Media> resourceMedias, SummaryType summaryType) {
        var promptText = this.prompt.getDocSummaryPrompt();
        promptText = promptText.replace("{SUMMARY_TYPE}", summaryType.getDisplayName());

        var docSummaryPrompt = Prompt.builder().messages(
                SystemMessage.builder().text(promptText).build(),
                UserMessage.builder().media(resourceMedias).text("extract document summary").build()
        ).build();

        log.info("Prompt: {}", docSummaryPrompt);
        var response = this.chatModel.call(docSummaryPrompt);
        log.info("Received response from chat model for summary of summaries: {}", response.getResult().getOutput().getText());

        return convertToJSONNode(response.getResult().getOutput().getText(), "summary");
    }

    @SneakyThrows
    private JsonNode processResourceText(String inputText, SummaryType summaryType, Language summaryLanguage) {
        var docSummaryOfSummariesUserPrompt = this.prompt.getDocSummaryOfSummariesUserPrompt();
        var docSummaryOfSummariesPrompt = this.prompt.getDocSummaryOfSummariesPrompt();

        docSummaryOfSummariesUserPrompt = docSummaryOfSummariesUserPrompt.replace("{SUMMARY_TYPE}", summaryType.getDisplayName());
        docSummaryOfSummariesUserPrompt = docSummaryOfSummariesUserPrompt.replace("{SUMMARY_LANGUAGE}", summaryLanguage.getDisplayName());
        docSummaryOfSummariesUserPrompt = docSummaryOfSummariesUserPrompt.replace("{INPUT_TEXT}", inputText);

        var docSummaryPrompt = Prompt.builder().messages(
                SystemMessage.builder().text(docSummaryOfSummariesPrompt).build(),
                UserMessage.builder().text(docSummaryOfSummariesUserPrompt).build()
        ).build();

        log.debug("Prompt: {}", docSummaryPrompt.getContents());
        var modelInferenceStartTime = System.currentTimeMillis();
        var response = this.chatModel.call(docSummaryPrompt);
        log.debug("Received response from chat model for summary of summaries: {}", response.getResult().getOutput().getText());
        log.debug("Chat model inference time is: {} in Millis", (System.currentTimeMillis() - modelInferenceStartTime));

        return convertToJSONNode(response.getResult().getOutput().getText(), "summary");
    }

    public List<ByteArrayResource> convertPDFToImage(File file, int fromPage, int noOfPages) {
        List<ByteArrayResource> images = new ArrayList<>();
        int startPage = fromPage - 1;

        try (PDDocument document = Loader.loadPDF(file)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            int endPage = Math.min(startPage + noOfPages, pageCount);

            if (startPage < 0 || startPage >= pageCount) {
                throw new IllegalArgumentException("fromPage is out of PDF page range.");
            }

            for (int i = startPage; i < endPage; i++) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(i, getRecommendedDpiPerPage(document.getPage(i), 1000));
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bim, "png", baos);
                images.add(new ByteArrayResource(baos.toByteArray()));
            }
        } catch (IOException e) {
            log.error("Failed to convert PDF to image", e);
            throw new RuntimeException(e);
        }
        return images;
    }

    private int getRecommendedDpiPerPage(PDPage page, int maxPixels) throws IOException {
        PDRectangle mediaBox = page.getMediaBox();
        float widthInches = mediaBox.getWidth() / 72f;
        float heightInches = mediaBox.getHeight() / 72f;
        float longestInches = Math.max(widthInches, heightInches);
        float dpi = (float) maxPixels / longestInches;

        boolean hasImages = false;
        PDResources resources = page.getResources();
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject) {
                hasImages = true;
                break;
            }
        }

        int finalDpi = Math.max((int) dpi, 10);
        log.info("Derived page dpi {} (images present: {})", finalDpi, hasImages);
        return finalDpi;
    }

    @SneakyThrows
    public JsonNode chatProcess(String docId, String question) {
        List<AIChatDto> chatHistory = new ArrayList<>();
        AIChatDto dto = new AIChatDto();
        dto.setMessageType(MessageType.USER);
        dto.setMessage(question);
        chatHistory.add(dto);
        return chatProcessWithHistory(docId, chatHistory);
    }

    @SneakyThrows
    public JsonNode chatProcessWithHistory(String docId, List<AIChatDto> chatHistory) {
        var resource = externalAPIService.getFileResource(docId);
        String inputText = resourceTextExtractionService.extractTextFromResource(resource, docId);

        String latestQuestion = extractLatestUserQuestion(chatHistory);
        List<DocumentChunk> chunks = buildDocumentChunks(inputText, docId);
        List<DocumentChunk> relevantChunks = selectRelevantChunks(chunks, latestQuestion, 8);
        String chunkContext = buildChunkContext(relevantChunks);

        String systemPrompt = prompt.getDocSystemChatWithReferencesPrompt()
        .replace("{DOCUMENT_CHUNKS}", chunkContext);

        String userPrompt = prompt.getDocUserChatPrompt()
                .replace("{USER_QUESTION}", latestQuestion);

        List<Message> messages = new ArrayList<>();
        messages.add(SystemMessage.builder().text(systemPrompt).build());

        if (chatHistory != null && !chatHistory.isEmpty()) {
            for (int i = 0; i < chatHistory.size() - 1; i++) {
                AIChatDto dto = chatHistory.get(i);
                if (dto.getMessageType() == MessageType.ASSISTANT) {
                    messages.add(new AssistantMessage(dto.getMessage()));
                } else if (dto.getMessageType() == MessageType.USER) {
                    messages.add(UserMessage.builder().text(dto.getMessage()).build());
                }
            }
        }

        messages.add(UserMessage.builder().text(userPrompt).build());

        var finalChatPrompt = Prompt.builder().messages(messages).build();
        var rawResponse = chatModel.call(finalChatPrompt).getResult().getOutput().getText();

        return parseAnswerWithReferences(rawResponse, latestQuestion, relevantChunks);
    }

    private String extractLatestUserQuestion(List<AIChatDto> chatHistory) {
        for (int i = chatHistory.size() - 1; i >= 0; i--) {
            AIChatDto dto = chatHistory.get(i);
            if (dto.getMessageType() == MessageType.USER && dto.getMessage() != null && !dto.getMessage().isBlank()) {
                return dto.getMessage();
            }
        }
        throw new IllegalArgumentException("No user question found in chat history");
    }

    private List<DocumentChunk> buildDocumentChunks(String inputText, String docId) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (inputText == null || inputText.isBlank()) {
            return chunks;
        }
String[] pages = inputText.split("\\f");
if (pages.length <= 1) {
    pages = splitByApproxPages(inputText, 3500);
}

        int pageNo = 1;
        for (String pageText : pages) {
            if (pageText == null || pageText.isBlank()) {
                pageNo++;
                continue;
            }

            List<String> subChunks = splitIntoChunks(pageText, 1200);
            int chunkIndex = 1;
            for (String subChunk : subChunks) {
                if (subChunk.isBlank()) {
                    continue;
                }

                chunks.add(DocumentChunk.builder()
                        .chunkId(docId + "_P" + pageNo + "_C" + chunkIndex)
                        .page(pageNo)
                        .pageLabel(String.valueOf(pageNo))
                        .section(extractSectionTitle(subChunk))
                        .text(subChunk.strip())
                        .build());
                chunkIndex++;
            }
            pageNo++;
        }

        return chunks;
    }

    private String[] splitByApproxPages(String inputText, int maxCharsPerPage) {
        List<String> pages = new ArrayList<>();
        String text = inputText == null ? "" : inputText.strip();

        for (int i = 0; i < text.length(); i += maxCharsPerPage) {
            pages.add(text.substring(i, Math.min(text.length(), i + maxCharsPerPage)));
        }

         return pages.toArray(new String[0]);
    }

    private List<String> splitIntoChunks(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        String normalized = text == null ? "" : text.replace("\r", "").strip();

        if (normalized.length() <= maxChars) {
            chunks.add(normalized);
            return chunks;
        }

        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + maxChars, normalized.length());
            if (end < normalized.length()) {
                int lastBreak = Math.max(
                        normalized.lastIndexOf('\n', end),
                        normalized.lastIndexOf(' ', end)
                );
                if (lastBreak > start + (maxChars / 2)) {
                    end = lastBreak;
                }
            }
            chunks.add(normalized.substring(start, end).strip());
            start = end;
        }

        return chunks;
    }

    private String extractSectionTitle(String chunkText) {
        if (chunkText == null || chunkText.isBlank()) {
            return "Document Section";
        }

        String[] lines = chunkText.split("\\R");
        for (String line : lines) {
            String trimmed = line.strip();
            if (!trimmed.isBlank() && trimmed.length() <= 120) {
                return trimmed;
            }
        }
        return "Document Section";
    }

    private List<DocumentChunk> selectRelevantChunks(List<DocumentChunk> chunks, String question, int limit) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        String[] terms = question == null ? new String[0] : question.toLowerCase().split("\\W+");

        return chunks.stream()
                .map(chunk -> {
                    int score = 0;
                    String chunkText = chunk.getText() == null ? "" : chunk.getText().toLowerCase();
                    for (String term : terms) {
                        if (term != null && term.length() > 2 && chunkText.contains(term)) {
                            score++;
                        }
                    }
                    chunk.setScore(score);
                    return chunk;
                })
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .limit(limit)
                .toList();
    }

    private String buildChunkContext(List<DocumentChunk> chunks) {
        StringBuilder sb = new StringBuilder();

        for (DocumentChunk chunk : chunks) {
            sb.append("[CHUNK]\n");
            sb.append("chunkId: ").append(nullSafe(chunk.getChunkId())).append("\n");
            sb.append("page: ").append(chunk.getPage() == null ? "" : chunk.getPage()).append("\n");
            sb.append("pageLabel: ").append(nullSafe(chunk.getPageLabel())).append("\n");
            sb.append("section: ").append(nullSafe(chunk.getSection())).append("\n");
            sb.append("text: ").append(nullSafe(chunk.getText())).append("\n\n");
        }

        return sb.toString();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private JsonNode parseAnswerWithReferences(String rawResponse, String question, List<DocumentChunk> relevantChunks) {
        String cleaned = cleanModelOutput(rawResponse);

        try {
            JsonNode root = mapper.readTree(cleaned);

            ObjectNode result = mapper.createObjectNode();
            result.put("question", question);
            result.put("answer", root.path("answer").asText(""));

            ArrayNode references = mapper.createArrayNode();
            if (root.has("references") && root.get("references").isArray()) {
                for (JsonNode refNode : root.get("references")) {
                    ObjectNode ref = mapper.createObjectNode();
                    if (refNode.has("page") && !refNode.get("page").isNull()) {
                        ref.put("page", refNode.get("page").asInt());
                    } else {
                        ref.putNull("page");
                    }
                    ref.put("pageLabel", refNode.path("pageLabel").asText(null));
                    ref.put("section", refNode.path("section").asText(null));
                    ref.put("chunkId", refNode.path("chunkId").asText(null));
                    ref.put("snippet", refNode.path("snippet").asText(null));
                    if (refNode.has("score") && !refNode.get("score").isNull()) {
                        ref.put("score", refNode.get("score").asDouble());
                    }
                    references.add(ref);
                }
            }

            result.set("references", references);
            result.put("response", root.path("answer").asText(""));
            return result;

        } catch (Exception e) {
            log.warn("Failed to parse QnA JSON response. Falling back to plain answer. Raw response={}", rawResponse, e);

            ObjectNode fallback = mapper.createObjectNode();
            fallback.put("question", question);
            fallback.put("answer", cleaned);
            fallback.put("response", cleaned);

            ArrayNode references = mapper.createArrayNode();
            for (int i = 0; i < Math.min(2, relevantChunks.size()); i++) {
                DocumentChunk chunk = relevantChunks.get(i);
                ObjectNode ref = mapper.createObjectNode();
               if (chunk.getPage() != null) {
    ref.put("page", chunk.getPage());
} else {
    ref.putNull("page");
}
                ref.put("pageLabel", chunk.getPageLabel());
                ref.put("section", chunk.getSection());
                ref.put("chunkId", chunk.getChunkId());
                ref.put("snippet", abbreviate(chunk.getText(), 220));
                ref.put("score", chunk.getScore() == null ? 0 : chunk.getScore());
                references.add(ref);
            }

            fallback.set("references", references);
            return fallback;
        }
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").strip();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    @Data
    @Builder
    @AllArgsConstructor
    private static class DocumentChunk {
        private String chunkId;
        private Integer page;
        private String pageLabel;
        private String section;
        private String text;
        private Integer score;
    }
}