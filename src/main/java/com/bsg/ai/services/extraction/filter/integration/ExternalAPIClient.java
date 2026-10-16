package com.bsg.ai.services.extraction.filter.integration;

import com.bsg.ai.services.extraction.filter.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "bsg-api", url = "${external-apis.project.url}", configuration = BasicAuthClientConfig.class)
public interface ExternalAPIClient {

    @PostMapping("/sec/login")
    void login(@RequestParam("username") String username, @RequestParam("password") String password);

    @GetMapping("/masterdata/docTypes/")
    List<DocTypeResponse> getDocumentTypes();

    @GetMapping("/masterdata/{client}/docTypes/")
    List<DocTypeResponse> getDocumentTypesByClient(@PathVariable("client") String client);

    @PostMapping("/archive/docs/search/")
    DocumentSearchResponse searchDocuments(@RequestBody QueryRequest query);

    @GetMapping("/masterdata/subjects/")
    Map<String, Map<String, String>> getMasterDataSubjects();

    @GetMapping("/masterdata/{client}/subjects/")
    List<SubjectResponse2> getSubjectsByClient(@PathVariable("client") String client);

    @PostMapping("/masterdata/persons/search2/")
    PersonSearchResponse searchPersons(@RequestBody QueryRequest query);

    @GetMapping("/masterdata/{client}/companies/")
    List<CompanyResponse>  getCompaniesByClient(@PathVariable("client") String client);

    @GetMapping("/archive/docs/{docId}/content")
    ResponseEntity<Resource> getContent(@PathVariable("docId") String docId);
}
