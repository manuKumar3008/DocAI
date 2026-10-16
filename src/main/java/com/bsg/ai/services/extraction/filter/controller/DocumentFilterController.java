package com.bsg.ai.services.extraction.filter.controller;

import com.bsg.ai.services.extraction.filter.dto.DocumentSearchResponse;
import com.bsg.ai.services.extraction.filter.dto.SearchFieldResponse;
import com.bsg.ai.services.extraction.filter.service.DocumentFilterSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentFilterController {

    private final DocumentFilterSearchService documentFilterSearchService;

    @GetMapping("/search/filters")
    public SearchFieldResponse extractSearchFields(@RequestParam("query") String query, @RequestParam("client") String client, @RequestParam("subarchive") String subarchive) {
        return documentFilterSearchService.extractFilters(query, client, subarchive);
    }

}
