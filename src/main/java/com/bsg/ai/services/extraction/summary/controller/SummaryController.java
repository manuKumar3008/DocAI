package com.bsg.ai.services.extraction.summary.controller;

import com.bsg.ai.services.extraction.summary.constant.Language;
import com.bsg.ai.services.extraction.summary.constant.SummaryType;
import com.bsg.ai.services.extraction.summary.service.SummaryService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class SummaryController {

    private final SummaryService documentSummaryService;

    @GetMapping("/{docId}/summary")
public ResponseEntity<JsonNode> getDocumentSummery(
        @PathVariable("docId") String docId,
        @RequestParam(name = "type", defaultValue = "SHORT", required = false) SummaryType type,
        @RequestParam(name = "language", defaultValue = "GERMAN", required = false) Language language,
        @RequestParam(name = "storagePath") String storagePath) {

    var result = documentSummaryService.processResourceFromStorage(docId, type, language, storagePath);
    return ResponseEntity.ok(result);
}

    @PostMapping("/{docId}/query")
    public ResponseEntity<JsonNode> chat(
            @PathVariable("docId") String docId,
            @RequestBody List<AIChatDto> chat) {
        var result = documentSummaryService.chatProcessWithHistory(docId, chat);
        return ResponseEntity.ok(result);
    }

    @Data
    public static class AIChatDto {
        MessageType messageType;
        String message;
    }
}