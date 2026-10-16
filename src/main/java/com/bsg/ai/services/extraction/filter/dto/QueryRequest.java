package com.bsg.ai.services.extraction.filter.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QueryRequest {
    private Integer pageSize;
    private Integer pageNo;
    private Integer maxSize;
    private List<SortOrderExpressions> sortOrderExpressions;
    private List<Filter> filters;
}
