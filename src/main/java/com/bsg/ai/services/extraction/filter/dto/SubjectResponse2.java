package com.bsg.ai.services.extraction.filter.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SubjectResponse2 {
    private String subject;
    private Map<String, String> translations;
    private String docType;
}
