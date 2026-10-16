package com.bsg.ai.services.extraction.filter.dto;

import java.util.Map;

public class FilterVO {
    public record DocTypeLabelRecord(String shortName, Map<String, String> translations) {
    }

    public record SubjectLabelRecord(String subject, Map<String, String> translations) {
    }

    public record CompaniesLabelRecord(String shortName, String longName) {
    }
}
