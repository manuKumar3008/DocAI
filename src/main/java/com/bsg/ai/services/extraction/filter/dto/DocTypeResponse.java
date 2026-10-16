package com.bsg.ai.services.extraction.filter.dto;

import lombok.Data;

import java.util.Map;

@Data
public class DocTypeResponse {
    private String shortName;
    private Map<String, String> translations;
    private String subarchive;
}
