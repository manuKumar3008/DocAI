package com.bsg.ai.services.extraction.icrform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bsg.ai.services.config.ApplicationPrompt;
import com.bsg.ai.services.util.PdfProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IcrExtractionService {

    private final ChatModel chatModel;
    private final ApplicationPrompt prompt;
    private final ObjectMapper mapper;
    private final PdfProcessingService pdfProcessingService;

    private static final int DPI = 300;

    public JsonNode processResource(Resource resource) {
        return pdfProcessingService.processPdf(resource, this::processResourceMedia, DPI);
    }

    @SneakyThrows
    private JsonNode processResourceMedia(List<Media> resourceMedias) {

        Prompt docProcessingPrompt = Prompt.builder()
                .messages(
                        SystemMessage.builder()
                                .text(this.prompt.getDocProcessingPrompt())
                                .build(),

                        UserMessage.builder()
                                .media(resourceMedias)
                                .text("extract document details")
                                .build()
                )
                .build();

        log.info("Prompt: {}", docProcessingPrompt);

        var response = chatModel.call(docProcessingPrompt);

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

        try {

            if (text != null && text.contains("```json")) {
                text = text.replace("```json", "");
            }

            if (text != null && text.contains("```")) {
                text = text.replace("```", "");
            }

            return mapper.readTree(text);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse generated JSON: {}", text, e);
            throw new RuntimeException(e);
        }
    }
}