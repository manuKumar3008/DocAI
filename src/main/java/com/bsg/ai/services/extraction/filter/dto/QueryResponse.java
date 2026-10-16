package com.bsg.ai.services.extraction.filter.dto;

import lombok.Data;

import java.util.List;

@Data
public class QueryResponse<T> {
    private List<T> resultList;
    private int resultSize;
    private boolean truncated;
}
