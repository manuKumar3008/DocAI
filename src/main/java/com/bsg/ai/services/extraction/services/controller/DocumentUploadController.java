package com.bsg.ai.services.extraction.services.controller;

import com.bsg.ai.services.extraction.services.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/document-upload")
@RequiredArgsConstructor
public class DocumentUploadController {
    private final DocumentProcessingService documentProcessingService;

    @PostMapping("/documents")
    public ResponseEntity<String> uploadDocument(@RequestParam("file") MultipartFile documentFile, @RequestParam("documentId") String documentId,
                                                                  @RequestParam(name = "forceReindex", required = false, defaultValue = "false") boolean forceReindex) {
        if (documentFile.isEmpty()) {
            return ResponseEntity.badRequest().body("File is missing");
        }

        String uploadedDocumentId = documentProcessingService.ingestDocumentWithSemanticStitching(documentFile, documentId, forceReindex);
        return ResponseEntity.ok(uploadedDocumentId);
    }

    @DeleteMapping("/removeDoc")
    public ResponseEntity<String> removeDocument(@RequestParam("documentId") String documentId) {
        documentProcessingService.removeDocumentFromStore(documentId);
        return ResponseEntity.ok(documentId);
    }
}
