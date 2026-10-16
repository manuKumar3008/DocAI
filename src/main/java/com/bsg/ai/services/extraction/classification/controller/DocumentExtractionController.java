package com.bsg.ai.services.extraction.classification.controller;

import com.bsg.ai.services.extraction.classification.pojo.DocumentTypeCandidate;
import com.bsg.ai.services.extraction.classification.pojo.ExtractedField;
import com.bsg.ai.services.extraction.classification.pojo.OCROutput;
import com.bsg.ai.services.extraction.classification.service.ClassificationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/document-extraction")
@RequiredArgsConstructor
@Slf4j
public class DocumentExtractionController {

    private final ClassificationService classificationService;
    private final ObjectMapper objectMapper;

    @PostMapping("/extract")
    public ResponseEntity<?> extract(@RequestParam("file") MultipartFile file,
                                     @RequestParam("documentId") String documentId,
                                     @RequestParam("taxonomy") String taxonomyJson) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is missing");
        }

        List<DocumentTypeCandidate> taxonomy = objectMapper.readValue(
                taxonomyJson, new TypeReference<List<DocumentTypeCandidate>>() {});

        if (taxonomy.isEmpty()) {
            log.warn("[DocumentExtraction] documentId={} called with empty taxonomy", documentId);
        }

        JsonNode result = classificationService.processResource(file, documentId, taxonomy);
        OCROutput output = classificationService.convertToClassificationResponseDto(result);

        Map<String, Object> response = new LinkedHashMap<>();

        Map<String, Object> documentTypeResponse = new LinkedHashMap<>();
        List<Map<String, Object>> classifications = output.getClassifications();
        if (classifications != null && !classifications.isEmpty()) {
            Map<String, Object> top = classifications.get(0);
            documentTypeResponse.put("id", top.get("id"));
            documentTypeResponse.put("name", top.get("label"));
            documentTypeResponse.put("confidence", top.get("score"));
        } else {
            documentTypeResponse.put("id", null);
            documentTypeResponse.put("name", "UNKNOWN");
            documentTypeResponse.put("confidence", 0.0);
        }

        response.put("documentType", documentTypeResponse);
        response.put("confidenceScore", documentTypeResponse.get("confidence"));

 Map<String, Object> fields = new LinkedHashMap<>();
Map<String, Object> fieldConfidence = new LinkedHashMap<>();

// removed: the output.getProperties() loop that added Unterkategorie/Datum/Name unconditionally

Map<String, Object> requiredFieldMapping = new LinkedHashMap<>();
if (output.getRequiredFieldMapping() != null) {
    output.getRequiredFieldMapping().forEach((fieldName, extracted) -> {
        if (extracted != null) {
            fields.put(fieldName, extracted.getValue());
            fieldConfidence.put(fieldName, extracted.getConfidence());

            Map<String, Object> fieldData = new LinkedHashMap<>();
            fieldData.put("value", extracted.getValue());
            fieldData.put("confidence", extracted.getConfidence());
            requiredFieldMapping.put(fieldName, fieldData);
        }
    });
}

response.put("fields", fields);
response.put("fieldConfidence", fieldConfidence);
response.put("requiredFieldMapping", requiredFieldMapping);
// requiredFieldMapping can stay too, as extra detail, but fields/fieldConfidence now include everything

        log.info("[DocumentExtraction] documentId={} documentType={} confidence={}",
                documentId, documentTypeResponse.get("name"), documentTypeResponse.get("confidence"));

        return ResponseEntity.ok(response);
    }
}