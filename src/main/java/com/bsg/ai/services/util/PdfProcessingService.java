package com.bsg.ai.services.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfProcessingService {

    private final ObjectMapper mapper;

    @Value("${system.doc.classification.max.page:10}")
    private Integer maxPage;

    public JsonNode processPdf(Resource resource, Function<List<Media>, JsonNode> llmApiCall, int dpi) {
        var targetJson = mapper.createObjectNode();
        int fromPage = 1;
        boolean hasMorePages = true;

        while (hasMorePages) {
            try {
                var imageResources = convertPDFToImage(resource, fromPage, maxPage, dpi);
                if (imageResources.isEmpty()) {
                    hasMorePages = false;
                    continue;
                }

                List<Media> resourceMedias = imageResources.stream().map(imageResource -> Media.builder()
                        .data(imageResource)
                        .mimeType(MimeType.valueOf(MediaType.IMAGE_PNG_VALUE))
                        .build()).toList();

                var response = llmApiCall.apply(resourceMedias);
                mergeRecursively(targetJson, response);

                if (response != null && checkResponseHasAnyNull(targetJson)) {
                    log.info("Response has a NULL value: {} from Page: {} to Page: {}", response, fromPage, fromPage + maxPage);
                    fromPage += maxPage;
                } else {
                    hasMorePages = false;
                }

            } catch (IllegalArgumentException e) {
                log.info("Page end reached from Page: {} to Page: {}", fromPage, fromPage + maxPage);
                hasMorePages = false;
            }
        }
        return targetJson;
    }

    private List<ByteArrayResource> convertPDFToImage(Resource resource, int fromPage, int noOfPages, int dpi) {
        List<ByteArrayResource> images = new ArrayList<>();
        int startPage = fromPage - 1;

        try (PDDocument document = Loader.loadPDF(resource.getInputStream().readAllBytes())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            int endPage = Math.min(startPage + noOfPages, pageCount);

            if (startPage >= pageCount) {
                throw new IllegalArgumentException("fromPage is out of PDF page range.");
            }

            for (int i = startPage; i < endPage; i++) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(i, dpi);
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

    private void mergeRecursively(JsonNode jsonTo, JsonNode jsonFrom) {
        if (jsonFrom == null) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = jsonFrom.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode valueFrom = entry.getValue();

            if (valueFrom.isTextual() && "Not specified".equalsIgnoreCase(valueFrom.asText())) {
                continue;
            }

            JsonNode valueTo = jsonTo.get(key);

            if (valueTo != null && valueTo.isObject() && valueFrom.isObject()) {
                mergeRecursively(valueTo, valueFrom);
            } else {
                if (jsonTo instanceof ObjectNode) {
                    ((ObjectNode) jsonTo).set(key, valueFrom);
                }
            }
        }
    }

    private boolean checkResponseHasAnyNull(JsonNode jsonTree) {
        if (jsonTree == null) {
            return false;
        }
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
}
