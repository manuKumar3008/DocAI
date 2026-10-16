package com.bsg.ai.services.extraction.icrform.controller;

import com.bsg.ai.services.extraction.icrform.service.IcrExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/process-form")
@RequiredArgsConstructor
public class IcrExtractionController {

    private final IcrExtractionService icrExtractionService;

    @PostMapping()
    public ResponseEntity<?> processPdf(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is missing");
        }
        var result = icrExtractionService.processResource(file.getResource());
        return ResponseEntity.ok(result);
    }
}
