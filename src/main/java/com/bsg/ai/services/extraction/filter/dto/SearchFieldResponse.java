package com.bsg.ai.services.extraction.filter.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SearchFieldResponse {
    private long overallProcessingDurationInMs;
    private long ollamaServiceProcessingDurationInMs;
    private List<Filter> searchFields;
}
