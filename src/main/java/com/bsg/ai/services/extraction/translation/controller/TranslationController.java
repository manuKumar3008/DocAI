package com.bsg.ai.services.extraction.translation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.bsg.ai.services.extraction.summary.constant.Language;
import com.bsg.ai.services.extraction.translation.service.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class TranslationController {

    private final TranslationService translationService;

    /**
     * pages accepts either a single page ("5") or an inclusive range ("2-3").
     * Omit it entirely to translate the whole document, page by page.
     */
    @GetMapping("/{docId}/translate")
    public ResponseEntity<JsonNode> getDocumentTranslation(
            @PathVariable("docId") String docId,
            @RequestParam(name = "pages", required = false) String pages,
            @RequestParam(name = "language", defaultValue = "German") String languageValue,
            @RequestParam(name = "storagePath", required = false) String storagePath) {
        Language language = Language.fromValue(languageValue);
        var result = translationService.processResource(docId, language, pages, storagePath);
        return ResponseEntity.ok(result);
    }

    /**
     * Streaming counterpart of /translate: emits each {pageNo, translation} as an
     * SSE event as soon as that page finishes, instead of waiting for the whole
     * document. Same pages/language/storagePath semantics as the blocking endpoint.
     */
    @GetMapping(value = "/{docId}/translate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDocumentTranslation(
            @PathVariable("docId") String docId,
            @RequestParam(name = "pages", required = false) String pages,
            @RequestParam(name = "language", defaultValue = "German") String languageValue,
            @RequestParam(name = "storagePath", required = false) String storagePath) {
        Language language = Language.fromValue(languageValue);
        SseEmitter emitter = new SseEmitter(0L); // no timeout — large docs can take a while
        translationService.streamResource(docId, language, pages, storagePath, emitter);
        return emitter;
    }
}