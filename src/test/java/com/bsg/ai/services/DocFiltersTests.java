package com.bsg.ai.services;

import com.bsg.ai.services.extraction.filter.service.LLMService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest
public class DocFiltersTests {

    @Autowired
    private LLMService llmService;

    ObjectMapper mapper = new ObjectMapper();
    ArrayList<DocFiltersTests.PromptWithExpectedOutput> promptsWithExpectedOutput = new ArrayList<DocFiltersTests.PromptWithExpectedOutput>();

    public DocFiltersTests() {
        promptsWithExpectedOutput.add(DocFiltersTests.PromptWithExpectedOutput.builder()
                .prompt("Give me documents for the purchase orders")
                .expectedOutput(
                        List.of(
                                Map.of("field", "docIndex.subject", "value", "PO")
                        ))
                .build());
        promptsWithExpectedOutput.add(DocFiltersTests.PromptWithExpectedOutput.builder()
                .prompt("Give me Invoice documents")
                .expectedOutput(
                        List.of(
                                Map.of("field", "docIndex.subject", "value", "Invoice")
                        ))
                .build());
        promptsWithExpectedOutput.add(DocFiltersTests.PromptWithExpectedOutput.builder()
                .prompt("Give me documents of certificate")
                .expectedOutput(
                        List.of(
                                Map.of("field", "docIndex.docType", "value", "ABES")
                        ))
                .build());
        promptsWithExpectedOutput.add(DocFiltersTests.PromptWithExpectedOutput.builder()
                .prompt("Give me documents of NDA")
                .expectedOutput(
                        List.of(
                                Map.of("field", "docIndex.subject", "value", "NDA")
                        ))
                .build());
        promptsWithExpectedOutput.add(DocFiltersTests.PromptWithExpectedOutput.builder()
                .prompt("Give me documents of NDA from 1st Oct 2024 to next six months")
                .expectedOutput(
                        List.of(
                                Map.of("field", "docIndex.subject", "value", "NDA"),
                                Map.of("field", "docIndex.docDate", "value", List.of("2024-10-01T00:00:00", "2025-03-31T23:59:59"))
                        ))
                .build());
        promptsWithExpectedOutput.add(DocFiltersTests.PromptWithExpectedOutput.builder()
                .prompt("Give me Balance sheets of last year")
                .expectedOutput(
                        List.of(
                                Map.of("field", "docIndex.subject", "value", "Balance Sheets"),
                                Map.of("field", "docIndex.docDate", "value", List.of("2024-01-01T00:00:00", "2024-12-31T23:59:59"))
                        ))
                .build());
        promptsWithExpectedOutput.add(DocFiltersTests.PromptWithExpectedOutput.builder()
                .prompt("Geben Sie mir Dokumente f r die Bestellungen")
                .expectedOutput(
                        List.of(
                                Map.of("field", "docIndex.subject", "value", "PO")
                        ))
                .build());
        promptsWithExpectedOutput.add(DocFiltersTests.PromptWithExpectedOutput.builder()
                .prompt("Pull document tagged with Exit.")
                .expectedOutput(
                        List.of(
                                Map.of("field", "docIndex.docType", "value", "AAUS")
                        ))
                .build());
        promptsWithExpectedOutput.add(DocFiltersTests.PromptWithExpectedOutput.builder()
                .prompt("List me documents of person John Doe")
                .expectedOutput(
                        List.of(
                                Map.of("field", "docIndex.empNo", "value", "John Doe", "type", "NAME")
                        ))
                .build());
        promptsWithExpectedOutput.add(DocFiltersTests.PromptWithExpectedOutput.builder()
                .prompt("List me documents of employee 1001")
                .expectedOutput(
                        List.of(
                                Map.of("field", "docIndex.empNo", "value", "1001", "type", "CODE")
                        ))
                .build());


    }

    @Test
    void testChat() throws JsonProcessingException {
        SoftAssertions softly = new SoftAssertions();
        var client = "Adani";
        var subarchive = "Personalakte";
        promptsWithExpectedOutput.stream().forEach(
                promptWithExpectedOutput -> {
                    var chatRequest = new Prompt(promptWithExpectedOutput.getPrompt());
                    long startTime = System.nanoTime();
                    AtomicLong ollamaServiceDuration = new AtomicLong();
                    var chatResponse = llmService.generate(chatRequest.toString(), client, ollamaServiceDuration::set, subarchive);
                    long overallDuration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
                    promptWithExpectedOutput.setOverallTimeInMs(overallDuration);
                    promptWithExpectedOutput.setModelQueryTimeInMs(ollamaServiceDuration.get());
                    var expectedResult = promptWithExpectedOutput.getExpectedOutput();
                    softly.assertThat(chatResponse).as("Prompt: %s", promptWithExpectedOutput.getPrompt()).isEqualTo(expectedResult);
                }
        );
        softly.assertAll();

        // print taken time for each prompt
        promptsWithExpectedOutput.forEach(
                promptWithExpectedOutput -> {
                    System.out.println("Prompt: " + promptWithExpectedOutput.getPrompt() + ", Duration: " + promptWithExpectedOutput.getOverallTimeInMs() + "ms" + ", Model Query Duration: " + promptWithExpectedOutput.getModelQueryTimeInMs() + "ms");
                }
        );
    }

    @Data
    @Builder
    public static class PromptWithExpectedOutput {
        String prompt;
        List<Map<String, Object>> expectedOutput;
        Long overallTimeInMs;
        Long modelQueryTimeInMs;
    }

}
