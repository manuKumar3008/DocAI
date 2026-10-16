package com.bsg.ai.services.extraction.translation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.bsg.ai.services.config.ApplicationPrompt;
import com.bsg.ai.services.extraction.filter.service.ExternalAPIService;
import com.bsg.ai.services.ocr.service.TesseractOcrService;
import com.bsg.ai.services.extraction.summary.constant.Language;
import com.bsg.ai.services.util.ResourceTextExtractionService;
import com.bsg.ai.services.util.PdfExtractor;
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
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.ai.chat.prompt.ChatOptions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TranslationService {

    @Value("${app.storage.base-dir:}")
    private String baseStorageDir;

    private static final String MEDIA_PROMPT =
        "Translate this document to {TRANSLATION_LANGUAGE} and return only plain text. Do not return HTML, XML, Markdown, or code fences.";
    private static final double WORDS_PER_TOKEN = 0.75;
    private static final int MAX_CONTEXT_TOKENS = 130000;
    private static final int PROMPT_OVERHEAD = 500;

    private final ChatModel chatModel;
    private final ApplicationPrompt prompt;
    private final ObjectMapper mapper;
    private final ExternalAPIService externalAPIService;
    private final TesseractOcrService tessaractOcrService;
    private final PdfExtractor pdfExtractor;
    private final ResourceTextExtractionService resourceTextExtractionService;

    // Dedicated pool for streaming translation requests so they don't tie up
    // the servlet container's own request-handling threads while translating
    // page by page. If the app already has a shared task executor bean,
    // swap this out for that instead of a new pool here.
    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(4);

    public String processResourceWithOcr(String docId, Resource resource, Language language) {
        return processResourceWithOcr(docId, resource, language, null);
    }

    public String processResourceWithOcr(String docId, Resource resource, Language language, Integer pageNo) {
        String pageText = resourceTextExtractionService.extractTextFromResource(resource, docId, pageNo);
        log.debug("Can Process document result is: {}", canProcessDocument(pageText));

        String chatResponse = processResourceText(pageText, language);

        int pageTextWordCount = countWords(pageText);
        int llmTextWordCount = countWords(chatResponse);
        log.debug("OCR word count is: {} and LLM response word count is: {}", pageTextWordCount, llmTextWordCount);

        if (llmTextWordCount < pageTextWordCount) {
            log.warn("LLM response text is smaller then ocr extracted text");
        }
        return chatResponse;
    }

    private int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        String[] words = text.trim().split("\\W+");
        return words.length;
    }

    public boolean canProcessDocument(String text) {
        int estimatedTokens = (int) (text.split("\\s+").length / WORDS_PER_TOKEN);
        return (estimatedTokens + PROMPT_OVERHEAD) < MAX_CONTEXT_TOKENS;
    }

    /**
     * Translates a document. pages accepts:
     * - null/blank: every page in the document
     * - a single number, e.g. "5": just that page
     * - an inclusive range, e.g. "2-3": that span of pages
     * Always returns an array of {pageNo, translation} objects, even for a single page,
     * so the caller/frontend has one consistent shape to render. Page numbers are 0-based.
     */
    @SneakyThrows
    public JsonNode processResource(String docId, Language summaryLanguage, String pages, String storagePath) {
        Resource fileResource = resolveResource(docId, storagePath);
        List<Integer> pagesToProcess = resolvePagesToProcess(fileResource, pages);

        ArrayNode result = mapper.createArrayNode();
        for (Integer page : pagesToProcess) {
            ObjectNode pageNode = translateSinglePage(docId, fileResource, summaryLanguage, page);
            result.add(pageNode);
        }
        return result;
    }

    /**
     * Streaming counterpart of processResource: same page selection and translation
     * logic, but emits each page over SSE as soon as it's ready instead of waiting
     * for the whole document. Runs on a background thread so the SSE connection
     * returns to the caller immediately; the emitter is completed (or completed
     * with an error) once every page has been sent.
     */
    public void streamResource(String docId, Language language, String pages, String storagePath, SseEmitter emitter) {
        streamExecutor.execute(() -> {
            try {
                Resource fileResource = resolveResource(docId, storagePath);
                List<Integer> pagesToProcess = resolvePagesToProcess(fileResource, pages);

                for (Integer page : pagesToProcess) {
                    ObjectNode pageNode = translateSinglePage(docId, fileResource, language, page);
                    emitter.send(SseEmitter.event().data(mapper.writeValueAsString(pageNode)));
                }
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                log.error("Streaming translation failed for docId {}", docId, e);
                emitter.completeWithError(e);
            }
        });
    }

    /**
     * Resolves the source PDF, either from a storage path or by fetching it via
     * the external API. Shared by both the blocking and streaming translation paths.
     */
    private Resource resolveResource(String docId, String storagePath)
        throws IOException {
        if (StringUtils.hasText(storagePath)) {
            File file = resolveStorageFile(storagePath);
            if (!file.exists() || !file.isFile()) {
                throw new FileNotFoundException("File not found at storage path: " + (file != null ? file.getAbsolutePath() : storagePath));
            }
            return new FileSystemResource(file);
        }
        return externalAPIService.getFileResource(docId);
    }

    /**
     * Translates one page and wraps it as {pageNo, translation}. Shared by both
     * the blocking and streaming translation paths.
     */
    private ObjectNode translateSinglePage(String docId, Resource fileResource, Language language, Integer page) {
        String rawTranslation = this.processResourceWithOcr(docId, fileResource, language, page);
        String cleaned = cleanTranslationText(rawTranslation);

        ObjectNode pageNode = mapper.createObjectNode();
        pageNode.put("pageNo", page + 1);
        pageNode.put("translation", cleaned);
        return pageNode;
    }

    /**
     * Decides which pages to translate from the single "pages" param:
     * blank -> every page; "5" -> that page only; "2-3" -> that inclusive range.
     */
    private List<Integer> resolvePagesToProcess(Resource resource, String pages) throws IOException {
        if (!StringUtils.hasText(pages)) {
            int totalPages = getPageCount(resource);
            List<Integer> allPages = new ArrayList<>();
            for (int i = 0; i < totalPages; i++) {
                allPages.add(i);
            }
            return allPages;
        }
        return parsePages(pages);
    }

    /**
     * Parses "5" as a single page or "2-3" as an inclusive range.
     * Page numbers are 1-based.
* Internally they are converted to 0-based indexes.
     */
    private List<Integer> parsePages(String pages) {
        String trimmed = pages.trim();
        List<Integer> result = new ArrayList<>();

        if (trimmed.contains("-")) {
            String[] parts = trimmed.split("-");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid pages value, expected a single page like '5' or a range like '2-3'");
            }
int start;
int end;

try {
    start = Integer.parseInt(parts[0].trim());
    end = Integer.parseInt(parts[1].trim());
} catch (NumberFormatException e) {
    throw new IllegalArgumentException("Invalid pages value, expected a single page like '5' or a range like '2-3'");
}

if (start < 1 || end < 1) {
    throw new IllegalArgumentException("Page numbers must start from 1");
}

if (start > end) {
    throw new IllegalArgumentException("Start page cannot be greater than end page");
}

// Convert to 0-based indexes
for (int i = start - 1; i <= end - 1; i++) {
    result.add(i);
}
        } else {
int single;

try {
    single = Integer.parseInt(trimmed);
} catch (NumberFormatException e) {
    throw new IllegalArgumentException("Invalid pages value, expected a single page like '5' or a range like '2-3'");
}

if (single < 1) {
    throw new IllegalArgumentException("Page number must start from 1");
}

// Convert to 0-based index
result.add(single - 1);
        }
        return result;
    }

    private int getPageCount(Resource resource) throws IOException {
        try (PDDocument document = Loader.loadPDF(resource.getInputStream().readAllBytes())) {
            return document.getNumberOfPages();
        }
    }

    private File resolveStorageFile(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return null;
        }

        String cleanStoragePath = storagePath.trim().replace("\\", "/");

        if (cleanStoragePath.startsWith("./")) {
            cleanStoragePath = cleanStoragePath.substring(2);
        }

        if (cleanStoragePath.startsWith("/")) {
            cleanStoragePath = cleanStoragePath.substring(1);
        }

        if (cleanStoragePath.startsWith("app/")) {
            cleanStoragePath = cleanStoragePath.substring(4);
        }

        Path candidatePath = Paths.get(cleanStoragePath).normalize();

        if (candidatePath.isAbsolute()) {
            File absoluteFile = candidatePath.toFile();
            log.info("Resolved absolute storage path: {}", absoluteFile.getAbsolutePath());
            return absoluteFile;
        }

        Path resolvedPath;
        if (baseStorageDir == null || baseStorageDir.isBlank()) {
            resolvedPath = candidatePath;
        } else {
            resolvedPath = Paths.get(baseStorageDir).resolve(candidatePath).normalize();
        }

        File resolvedFile = resolvedPath.toFile();
        log.info(
            "Resolving document storage path. Base dir: [{}], Relative path received: [{}], Clean path: [{}], Final absolute path: [{}]",
            baseStorageDir, storagePath, cleanStoragePath, resolvedFile.getAbsolutePath()
        );

        return resolvedFile;
    }

    public JsonNode processResource(File file, Language language) {
        var imageResources = convertPDFToImage(file);
        List<Media> resourceMedias = imageResources.stream().map(imageResource -> {
            return Media.builder()
                    .data(imageResource)
                    .mimeType(MimeType.valueOf(String.valueOf(MediaType.IMAGE_PNG)))
                    .build();
        }).toList();
        return this.processResourceMedia(resourceMedias, language);
    }

    @SneakyThrows
    private String processResourceText(String text, Language language) {
        var promptText = this.prompt.getDocTranslationPrompt();
        promptText = promptText.replace("{TRANSLATION_LANGUAGE}", language.getDisplayName());

        var docTranslationPrompt = Prompt.builder().messages(
                        SystemMessage.builder().text(promptText).build(),
                        UserMessage.builder()
                                .text(text)
                                .build()
                )
                .chatOptions(ChatOptions.builder()
                    .temperature(0.0)
                    .build())
                .build();

        log.debug("Prompt: {}", docTranslationPrompt.toString());
        long llmStartTime = System.currentTimeMillis();

        var response = this.chatModel.call(docTranslationPrompt);
        log.info("Response time is: {} in millis and response from chat model: {}",
                (System.currentTimeMillis() - llmStartTime),
                response.getResult().getOutput().getText());

        return response.getResult().getOutput().getText();
    }

    @SneakyThrows
    private JsonNode processResourceMedia(List<Media> resourceMedias, Language language) {
        var promptText = this.prompt.getDocTranslationPrompt();
        promptText = promptText.replace("{TRANSLATION_LANGUAGE}", language.getDisplayName());

        var mediaPrompt = MEDIA_PROMPT.replace(
            "{TRANSLATION_LANGUAGE}",
            language.getDisplayName()
        );

        Prompt docTranslationPrompt = Prompt.builder()
                .messages(
                        SystemMessage.builder()
                                .text(promptText)
                                .build(),
                        UserMessage.builder()
                                .media(resourceMedias)
                                .text(mediaPrompt)
                                .build()
                )
                .chatOptions(ChatOptions.builder()
                .temperature(0.0)
                .build())
        .build();
                

        log.info("Prompt: {}", docTranslationPrompt);

        var response = this.chatModel.call(docTranslationPrompt);

        log.info("Received response from chat model: {}", response.getResult().getOutput().getText());

        return convertToJSONNode(response.getResult().getOutput().getText());
    }

    public List<ByteArrayResource> convertPDFToImage(File file) {
        List<ByteArrayResource> images = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(file)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            for (int i = 0; i < pageCount; i++) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(i, getRecommendedDpiPerPage(document.getPage(i), 800));
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

        // Page size in inches (1 point = 1/72 inch)
        float widthInches = mediaBox.getWidth() / 72f;
        float heightInches = mediaBox.getHeight() / 72f;

        // Longest side in inches
        float longestInches = Math.max(widthInches, heightInches);

        // Calculate DPI so that longest side = maxPixels
        float dpi = (float) maxPixels / longestInches;

        // Check if page contains images
        boolean hasImages = false;
        PDResources resources = page.getResources();
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject) {
                hasImages = true;
                break;
            }
        }

        // If purely text/vector-based, allow more compression
        if (!hasImages) {
            dpi = dpi * 0.5f; // reduce DPI by 50% for text pages
        }

        int finalDpi = Math.max((int) dpi, 10); // never go below 10 DPI
        log.info("Derived page dpi {} (images present: {})", finalDpi, hasImages);
        return finalDpi;
    }

    private JsonNode convertToJSONNode(String text) {
        ObjectNode jsonNode = mapper.createObjectNode();
        jsonNode.put("translation", cleanTranslationText(text));
        return jsonNode;
    }

   private String cleanTranslationText(String text) {
    if (text == null) {
        return "";
    }
    String cleaned = text;
    if (cleaned.contains("```html")) {
        cleaned = cleaned.replace("```html", "");
    }
    if (cleaned.contains("```plain")) {
        cleaned = cleaned.replace("```plain", "");
    }
    if (cleaned.contains("```text")) {
        cleaned = cleaned.replace("```text", "");
    }
    if (cleaned.contains("```")) {
        cleaned = cleaned.replace("```", "");
    }
    cleaned = cleaned.replaceAll("<[^>]+>", "");  
    return cleaned.trim();
}
}