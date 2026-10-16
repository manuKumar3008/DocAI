package com.bsg.ai.services.extraction.filter.service;

import com.bsg.ai.services.extraction.filter.dto.*;
import com.bsg.ai.services.extraction.filter.integration.ExternalAPIClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalAPIService {

    private final ExternalAPIClient externalAPIClient;

    public void login(String username, String password) {
        externalAPIClient.login(username, password);
    }

    public List<DocTypeResponse> getDocumentTypes() {
        return externalAPIClient.getDocumentTypes();
    }
    @Cacheable(value = "documentTypesByClient", key = "#clientId")
    public List<DocTypeResponse> getDocumentTypes(String clientId) {
        return externalAPIClient.getDocumentTypesByClient(clientId);
    }

    public Map<String, Map<String, String>> getMasterDataSubjects() {
        return externalAPIClient.getMasterDataSubjects();
    }
    @Cacheable(value = "subjectsByClient", key = "#clientId")
    public List<SubjectResponse2> getSubjectsResponseList(String clientId) {
        return externalAPIClient.getSubjectsByClient(clientId);
    }

    public DocumentSearchResponse searchDocuments(QueryRequest query) {
        return externalAPIClient.searchDocuments(query);
    }
    @Cacheable(value = "companiesByClient", key = "#clientId")
    public List<CompanyResponse> getCompaniesByClient(String clientId) {
        return externalAPIClient.getCompaniesByClient(clientId);
    }

    public PersonSearchResponse searchPersons(QueryRequest query) {
        return externalAPIClient.searchPersons(query);
    }

    public File getFile(String docId) throws IOException {
        byte[] fileBytes = getFileResource(docId).getContentAsByteArray();
        // Create a temporary file (auto-deleted on JVM exit)
        File tempFile = File.createTempFile("document_" + docId, ".pdf");

        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(fileBytes);
        }
        return tempFile;
    }

    public Resource getFileResource(String docId) throws IOException {
        var responseEntityResource = externalAPIClient.getContent(docId);
        if (responseEntityResource.getBody() == null || !responseEntityResource.getBody().exists()) {
            throw new RuntimeException("File not found");
        }
        return responseEntityResource.getBody();
    }


    public Map<String, Map<String, String>> getMasterDataSubjectsDemo() {
//		return externalAPIClient.getMasterDataSubjects();

        Map<String, Map<String, String>> subjects = Map.ofEntries(
                entry("VERTRAG", Map.of(
                        "Sonstiges", "Other",
                        "Anstellungsvertrag", "Employment contract",
                        "Traineevertrag", "Trainee contract",
                        "Befristeter Trainee Vertrag", "Fixed-term trainee contract",
                        "Dienstvertrag", "Service contract"
                )),
                entry("BEWERBUNG", Map.of(
                        "Anschreiben", "Cover letter",
                        "Bewerbungsunterlagen", "Application documents"
                )),
                entry("DATZ", Map.of(
                        "Datenschutzerklärung", "Privacy policy"
                )),
                entry("JUBI", Map.of(
                        "Arbeitsjubiläum", "Work anniversary"
                )),
                entry("NETK", Map.of(
                        "Genehmigung", "Approval",
                        "Antrag", "Request"
                )),
                entry("SOZI", Map.of(
                        "Sozialversicherungsausweis", "Social security card"
                )),
                entry("VERS", Map.of(
                        "Unterrichtung Betriebsrat", "Works council correspondence"
                )),
                entry("ZEUGNIS", Map.of(
                        "Zertifikat", "Certificate",
                        "Zeugnis", "Reference"
                )),
                entry("VERD", Map.of(
                        "Verdienstabrechnung", "Income statement"
                )),
                entry("AODA", Map.of(
                        "AO-Datei", "AO file"
                )),
                entry("SV", Map.of(
                        "Bescheinigung zur Sozialversicherung", "Social security certificate"
                )),
                entry("AN", Map.of(
                        "Lohnsteuerbescheinigung", "Income tax certificate"
                )),
                entry("BS", Map.of(
                        "Bescheinigung", "Certification"
                )),
                entry("STAM", Map.of(
                        "NAME_CHANGE", "Name Change",
                        "ADDRESS_CHANGE", "Adress change",
                        "CHANGE_HEALTH_INSUR", "Change health insurance",
                        "OTHER_NOTIFICATION", "Other notifications",
                        "BANK_CHANGE", "Bank change",
                        "SICK_LEAVE", "Sick note"
                )),
                entry("BV", Map.of(
                        "Betriebsordnung", "Operating regulations",
                        "Betriebsvereinbarung", "Company agreement",
                        "Sonstiges", "Other"
                )),
                entry("REV", Map.of(
                        "Sonstiges", "Other"
                ))
//                entry("Literature", Map.of(
//                        "Classic Fiction", "Classic Fiction",
//                        "Drama", "Drama",
//                        "Poetry", "Poetry"
//                )),
//
//                entry("Science", Map.of(
//                        "Physics", "Physics",
//                        "Mathematics", "Mathematics",
//                        "Biology", "Biology"
//                )),
//
//                entry("Computer Science", Map.of(
//                        "Programming", "Programming",
//                        "Software Engineering", "Software Engineering",
//                        "Data Science", "Data Science"
//                )),
//
//                entry("Philosophy", Map.of(
//                        "Ancient Philosophy", "Ancient Philosophy",
//                        "Modern Philosophy", "Modern Philosophy"
//                )),
//
//                entry("History", Map.of(
//                        "Biography", "Biography",
//                        "World History", "World History"
//                ))
        );

        return subjects;
    }

    @Cacheable(value = "subjects", key = "'subjects'", cacheManager = "cacheManagerOneDay")
    public SubjectsResponse getSubjectsResponse() {
        Map<String, Map<String, String>> subjects = getMasterDataSubjectsDemo();
        return SubjectsResponse.builder()
                .documentTypes(subjects.keySet().stream().toList())
                .subjects(subjects.values().stream().flatMap(map -> map.keySet().stream()).toList())
                .documentTypeWiseSubjects(subjects.entrySet().stream().map(entry -> {
                    var documentTypeWiseSubjects = new SubjectsResponse.DocumentTypeWiseSubjects();
                    documentTypeWiseSubjects.documentType = entry.getKey();
                    documentTypeWiseSubjects.subjects = entry.getValue().entrySet().stream().map(subject -> {
                        var subjectKeyValue = new SubjectsResponse.SubjectKeyValue();
                        subjectKeyValue.key = subject.getKey();
                        subjectKeyValue.value = subject.getValue();
                        return subjectKeyValue;
                    }).toList();
                    return documentTypeWiseSubjects;
                }).toList())
                .build();
    }
}
