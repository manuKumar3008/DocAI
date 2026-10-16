package com.bsg.ai.services.extraction.filter.dto;

import java.util.Map;

public interface PromptRequestContent {
    record PromptCompanyRequest(String shortName, String longName) { }
    record PromptSubjectRequest(String subject, Map<String, String> translations) { }
    record PromptDocTypeRequest(String shortName, Map<String, String> translations) { }
}
