package com.bsg.ai.services.extraction.classification.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SummaryResult {
    @JsonProperty("group_index")
    private int groupIndex;
    @JsonProperty("key_entities")
    private List<Entity> keyEntities;
    private List<String> dates;
    private List<String> numbers;
    @JsonProperty("key_facts")
    private List<String> keyFacts;
    @JsonProperty("compressed_summary")
    private String compressedSummary;
}
