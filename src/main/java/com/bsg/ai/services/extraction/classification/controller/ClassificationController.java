package com.bsg.ai.services.extraction.classification.controller;

import com.bsg.ai.services.extraction.classification.pojo.DocumentTypeCandidate;
import com.bsg.ai.services.extraction.classification.service.ClassificationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/document-classification")
@RequiredArgsConstructor
@Slf4j
public class ClassificationController {

    private final ClassificationService classificationService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<?> getDocumentClassification(
            @RequestParam("file") MultipartFile documentFile,
            @RequestParam("documentId") String documentId,
            @RequestParam("taxonomy") String taxonomyJson
    ) throws Exception {

        log.info("ClassificationController request received: documentId={}, fileName={}",
                documentId, documentFile.getOriginalFilename());

        if (documentFile.isEmpty()) {
            return ResponseEntity.badRequest().body("File is missing");
        }

        List<DocumentTypeCandidate> taxonomy = objectMapper.readValue(
                taxonomyJson,
                new TypeReference<List<DocumentTypeCandidate>>() {}
        );

        JsonNode result = classificationService.processResource(documentFile, documentId, taxonomy);
        return ResponseEntity.ok(result);
    }
}