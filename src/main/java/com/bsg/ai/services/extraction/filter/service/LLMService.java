package com.bsg.ai.services.extraction.filter.service;
import org.springframework.ai.chat.prompt.Prompt;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bsg.ai.services.config.ApplicationPrompt;
import com.bsg.ai.services.extraction.filter.dto.DocTypeResponse;
import com.bsg.ai.services.extraction.filter.dto.ElapsedTimeUpdater;
import com.bsg.ai.services.extraction.filter.dto.FilterVO;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LLMService {

    private final ChatModel chatModel;
    private final ApplicationPrompt prompt;
    private final ExternalAPIService externalAPIService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${system.doc.classification.max.page:10}")
    private Integer maxPage;

    private static final String SAMPLE_DOCUMENT_TYPE_WISE_SUBJECTS = "[{\"subject\":\"subject sample 1\",\"translations\":{\"de_DE\":\"Betreff Beispiel 1\",\"en_US\":\"Subject Sample 1\"}},{\"subject\":\"subject sample 2\",\"translations\":{\"de_DE\":\"Betreff Beispiel 2\",\"en_US\":\"Subject Sample 2\"}}]";
    private static final String SAMPLE_DOCUMENT_TYPE_LABELS_BY_CODE = "[{\"shortName\":\"DOCTYPESAMPLE1\",\"translations\":{\"en_US\":\"document type sample 1\",\"de_DE\":\"Dokumenttyp Beispiel 1\"}},{\"shortName\":\"DOCTYPESAMPLE2\",\"translations\":{\"en_US\":\"document type sample 2\",\"de_DE\":\"Dokumenttyp Beispiel 2\"}}]";
    private static final String SAMPLE_COMPANIES = "[{\"shortName\":\"COM1\",\"longName\":\"Sample Company 1\"},{\"shortName\":\"COM2\",\"longName\":\"Sample Company 2\"}]";
    private static final String SAMPLE_DOCUMENT_TYPE_1 = "Dokumenttyp Beispiel 1";
    private static final String SAMPLE_DOCUMENT_TYPE_1_KEY = "DOCTYPESAMPLE1";
    private static final String SAMPLE_DOCUMENT_TYPE_2 = "Dokumenttyp Beispiel 2";
    private static final String SAMPLE_DOCUMENT_TYPE_2_KEY = "DOCTYPESAMPLE2";
    private static final String SAMPLE_SUBJECT_1 = "Betreff Beispiel 1";
    private static final String SAMPLE_SUBJECT_1_KEY = "subject sample 1";
    private static final String SAMPLE_SUBJECT_2 = "Betreff Beispiel 2";
    private static final String  SAMPLE_SUBJECT_2_KEY = "subject sample 2";
    private static final String SAMPLE_COMPANY_1 = "Sample Company 1";
    private static final String SAMPLE_COMPANY_1_KEY = "COM1";

    @SneakyThrows
    public List<Map<String, Object>> generate(String message, String client, ElapsedTimeUpdater elapsedTimeUpdater, String subarchive) {
        var documentTypeWiseSubjects = SAMPLE_DOCUMENT_TYPE_WISE_SUBJECTS;
        var documetTypeLabelsByCode = SAMPLE_DOCUMENT_TYPE_LABELS_BY_CODE;
        var companies = SAMPLE_COMPANIES;
        var exampleDocType1 = SAMPLE_DOCUMENT_TYPE_1;
        var exampleDocType1Key = SAMPLE_DOCUMENT_TYPE_1_KEY;
        var exampleDocType2 = SAMPLE_DOCUMENT_TYPE_2;
        var exampleDocType2Key = SAMPLE_DOCUMENT_TYPE_2_KEY;
        var exampleCompany1 = SAMPLE_COMPANY_1;
        var exampleCompany1Key = SAMPLE_COMPANY_1_KEY;
        var exampleSubject1 = SAMPLE_SUBJECT_1;
        var exampleSubject1Key = SAMPLE_SUBJECT_1_KEY;
        var exampleSubject2 = SAMPLE_SUBJECT_2;
        var exampleSubject2Key = SAMPLE_SUBJECT_2_KEY;

        var systemDate = (new Date()).toString();
        try {
            var docTypes = Optional.ofNullable(externalAPIService.getDocumentTypes(client)).orElse(List.of());
            var filteredDocTypes = docTypes.stream().filter(dt -> subarchive.equals(dt.getSubarchive())).toList();

            var allowedDocTypes = filteredDocTypes.stream().map(DocTypeResponse::getShortName).collect(Collectors.toSet());

            var subjects = Optional.ofNullable(externalAPIService.getSubjectsResponseList(client)).orElse(List.of());
            var filteredSubjects = subjects.stream().filter(subject -> allowedDocTypes.contains(subject.getDocType())).toList();

            var docTypeLabelRecords = filteredDocTypes.stream().map(dt -> new FilterVO.DocTypeLabelRecord(dt.getShortName(), dt.getTranslations())).collect(Collectors.toSet());

            var subjectLabelRecords = filteredSubjects.stream().map(s -> new FilterVO.SubjectLabelRecord(s.getSubject(), s.getTranslations())).collect(Collectors.toSet());

            var companiesList = Optional.ofNullable(externalAPIService.getCompaniesByClient(client)).orElse(List.of());
            var filteredComp = companiesList.stream().filter(company -> subarchive.equals(company.getSubarchive())).toList();
            var companiesLabelRecords = filteredComp.stream().map(company -> new FilterVO.CompaniesLabelRecord(company.getShortName(), company.getLongName())).collect(Collectors.toList());
            mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

            // replace the sample data with the actual data
            documentTypeWiseSubjects = mapper.writeValueAsString(subjectLabelRecords);
            documetTypeLabelsByCode = mapper.writeValueAsString(docTypeLabelRecords);
            companies = mapper.writeValueAsString(companiesLabelRecords);

            // set the examples
            var docType1 = filteredDocTypes.stream()
                    .filter(dt -> dt.getTranslations().containsKey("de_DE"))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No de_DE available for doc types"));
            var docType2 = filteredDocTypes.stream()
                    .filter(dt -> dt.getTranslations().containsKey("de_DE"))
                    .skip(1)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No de_DE available for doc types"));
            exampleDocType1 = docType1.getTranslations().get("de_DE");
            exampleDocType1Key = docType1.getShortName();
            exampleDocType2 = docType2.getTranslations().get("de_DE");
            exampleDocType2Key = docType2.getShortName();
            exampleCompany1 = filteredComp.get(0).getLongName();
            exampleCompany1Key = filteredComp.get(0).getShortName();
            var subject1 = filteredSubjects.stream()
                    .filter(dt -> dt.getTranslations().containsKey("de_DE"))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No de_DE available for subjects"));
            var subject2 = filteredSubjects.stream()
                    .filter(dt -> dt.getTranslations().containsKey("de_DE"))
                    .skip(1)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No de_DE available for subjects"));
            exampleSubject1 = subject1.getTranslations().get("de_DE");
            exampleSubject1Key = subject1.getSubject();
            exampleSubject2 = subject2.getTranslations().get("de_DE");
            exampleSubject2Key = subject2.getSubject();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        Map<String, String> dataMapper = new HashMap<>();
        dataMapper.put("DOC_TYPE_MAPPING", documetTypeLabelsByCode);
        dataMapper.put("SUBJECT_MAPPING", documentTypeWiseSubjects);
        dataMapper.put("COMPANY_MAPPING", companies);
        dataMapper.put("SYSTEM_DATE", systemDate);
        dataMapper.put("EXAMPLE_DOC_TYPE_1", exampleDocType1);
        dataMapper.put("EXAMPLE_DOC_TYPE_1_KEY", exampleDocType1Key);
        dataMapper.put("EXAMPLE_DOC_TYPE_2", exampleDocType2);
        dataMapper.put("EXAMPLE_DOC_TYPE_2_KEY", exampleDocType2Key);
        dataMapper.put("EXAMPLE_COMPANY_1", exampleCompany1);
        dataMapper.put("EXAMPLE_COMPANY_1_KEY", exampleCompany1Key);
        dataMapper.put("EXAMPLE_SUBJECT_1", exampleSubject1);
        dataMapper.put("EXAMPLE_SUBJECT_1_KEY", exampleSubject1Key);
        dataMapper.put("EXAMPLE_SUBJECT_2", exampleSubject2);
        dataMapper.put("EXAMPLE_SUBJECT_2_KEY", exampleSubject2Key);

        var systemPrompt = this.prompt.getSystemPrompt(dataMapper);
        var systemInstructionMessage = SystemMessage.builder().text(systemPrompt).build();
        var userInstructionMessage = UserMessage.builder().text(message).build();
        Prompt promptMessagePrompt =new Prompt(List.of(systemInstructionMessage, userInstructionMessage));
        log.debug("Prompt: {}", promptMessagePrompt.toString());

        long startTime = System.nanoTime();
        var response = this.chatModel.call(promptMessagePrompt);
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        if (elapsedTimeUpdater != null) {
            elapsedTimeUpdater.update(duration);
        }
        log.info("Query|{}|LLM Time (ms)|{}", message, duration);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            log.error("Received null response from chat model");
            return List.of();
        }

        String text = response.getResult().getOutput().getText();
        log.debug("Received response from chat model: {}", text);
        return text != null ? parseGeneratedJSON(message, text) : List.of();
    }

    private List<Map<String, Object>> parseGeneratedJSON(String prompt, String text) {
        try {
            if (text != null && text.contains("```json")) {
                text = text.replace("```json", "");
            }
            if (text != null && text.contains("```")) {
                text = text.replace("```", "");
            }
            if (text != null && !text.isEmpty() && text.trim().charAt(0) != '[') {
                text = "[" + text + "]";
            }
            return mapper.readValue(text, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("Prompt: {}\nFailed to parse generated JSON: {}", prompt, text, e);
            throw new RuntimeException(e);
        }
    }

}
