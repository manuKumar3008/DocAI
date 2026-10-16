package com.bsg.ai.services.extraction.filter.service;

import com.bsg.ai.services.extraction.filter.dto.DocumentSearchResponse;
import com.bsg.ai.services.extraction.filter.dto.Filter;
import com.bsg.ai.services.extraction.filter.dto.QueryRequest;
import com.bsg.ai.services.extraction.filter.dto.SearchFieldResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentFilterSearchService {

    private final LLMService llmService;
    private final ExternalAPIService externalAPIService;
    private final AtomicLong totalQueriesExecuted = new AtomicLong(0L);
    private final AtomicLong averageQueryDuration = new AtomicLong(0L);
    private final AtomicLong averageOllamaServiceDuration = new AtomicLong(0L);

    private void addDefaultFilters(List<Filter> filters) {
        // Note: Below is a sample filter to add a default one.
        // filters.add(Filter.builder().field("subarchive").value("Personalakte").build());
    }

    private List<Filter> buildFilterList(List<Map<String, Object>> extractedData, Map<String, String> customDefaultFilters) {
        var filters = new ArrayList<Filter>();
        addDefaultFilters(filters);
        addCustomFilters(filters, customDefaultFilters);
        extractedData.forEach(data -> {
            var key = data.getOrDefault("field", null);
            if (key != null) {
                if (key.equals("docIndex.empNo") && data.getOrDefault("type", null) != null && data.getOrDefault("type", null).equals("NAME")) {
                    var value = data.getOrDefault("value", null);
                    if (value != null) {
                        filters.add(Filter.builder().field((String) key).value(extractPersonCode((String) value)).build());
                        return;
                    }
                }
                var value = data.getOrDefault("value", null);
                filters.add(Filter.builder().field((String) key).value(value).build());
            }
        });

        return filters;
    }

    private void addCustomFilters(ArrayList<Filter> filters, Map<String, String> customDefaultFilters) {
        if (customDefaultFilters != null && !customDefaultFilters.isEmpty()) {
            customDefaultFilters.forEach((key, value) -> filters.add(Filter.builder().field(key).value(value).build()));
        }
    }

    private String extractPersonCode(String value) {
        String personCode = "";
        var name = value.split(" ");
        var query = QueryRequest.builder().pageNo(1).pageSize(10).maxSize(0)
                .filters(new ArrayList<>()).build();
        if (name.length > 1) {
            query.getFilters().add(Filter.builder().field("firstName").value("%" + name[0] + "%").operator("like").build());
            query.getFilters().add(Filter.builder().field("surName").value("%" + name[1] + "%").operator("like").build());
        } else {
            query.getFilters().add(Filter.builder().field("firstName").value("%" + name[0] + "%").operator("like").build());
        }
        var personSearchResponse = externalAPIService.searchPersons(query);
        if (!personSearchResponse.getResultlist().isEmpty()) {
            personCode = personSearchResponse.getResultlist().get(0).getEmpNo();
        }
        return personCode;
    }

    public SearchFieldResponse extractFilters(String query, String client, String subarchive) {
        long startTime = System.nanoTime();
        AtomicLong ollamaServiceDuration = new AtomicLong();
        var extractedData = llmService.generate(query, client, ollamaServiceDuration::set, subarchive);
        var filters = buildFilterList(extractedData, Map.of("subarchive", subarchive));
        long overallDuration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        log.info("Query|{}|Time (ms)|{}", query, overallDuration);
        // logProcessingTimeTogether(query, overallDuration, ollamaServiceDuration.get());
        return SearchFieldResponse.builder()
                .searchFields(filters)
                .ollamaServiceProcessingDurationInMs(ollamaServiceDuration.get())
                .overallProcessingDurationInMs(overallDuration)
                .build();
    }

    public DocumentSearchResponse searchDocuments(String query, String client, String subarchive) {
        long startTime = System.nanoTime();
        AtomicLong ollamaServiceDuration = new AtomicLong();
        var extractedData = llmService.generate(query, client, ollamaServiceDuration::set, subarchive);
        var filters = buildFilterList(extractedData, Map.of("subarchive", subarchive));
        var queryRequest = QueryRequest.builder()
                .pageNo(1)
                .pageSize(50)
                .maxSize(0)
                .filters(filters).build();
        long overallDuration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        var documents = externalAPIService.searchDocuments(queryRequest);
        log.info("Query|{}|Time (ms)|{}", query, overallDuration);
        // logProcessingTimeTogether(query, overallDuration, ollamaServiceDuration.get());
        return documents;
    }

    /**
     * This method logs the processing time of a query together with the processing time of the OllamaService. Along with aggregated time as well.
     *
     * @param query
     * @param overallDuration
     * @param ollamaServiceDuration
     */
    private void logProcessingTimeTogether(String query, long overallDuration, long ollamaServiceDuration) {
        // The idea here is to calculate a running average of how long each query takes (averageQueryDuration) and how long the OllamaService takes (averageOllamaServiceDuration).
        // The formula is: (previousAverage * (totalQueriesExecuted - 1) + newDuration) / totalQueriesExecuted
        // This is equivalent to summing up all the durations and dividing by the total number of queries.
        // The reason for using this formula instead of just summing up the durations and dividing is to avoid overflow of the sum.
        // By using the previous average and the new duration, we avoid having to keep track of the sum of all durations, which could be very large for a large number of queries.
        var tqe = totalQueriesExecuted.incrementAndGet();
        var aqd = averageQueryDuration.accumulateAndGet(overallDuration, (a, b) -> (a * (totalQueriesExecuted.get() - 1) + b) / totalQueriesExecuted.get());
        var oqd = averageOllamaServiceDuration.accumulateAndGet(ollamaServiceDuration, (a, b) -> (a * (totalQueriesExecuted.get() - 1) + b) / totalQueriesExecuted.get());
        log.info("Query|{}|OllamaService call Duration|{} ms|Total Duration|{} ms|Total Queries Executed|{}|Average Query Duration|{} ms|Average OllamaService Duration|{} ms", query, ollamaServiceDuration, overallDuration, tqe, aqd, oqd);
    }
}
