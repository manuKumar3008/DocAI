package com.bsg.ai.services;

import com.bsg.ai.services.extraction.filter.service.ExternalAPIService;
import com.bsg.ai.services.extraction.summary.service.SummaryService;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@Slf4j
public class DocChatTests {

    @Autowired
    private SummaryService docSummaryService;

    @MockitoBean
    private ExternalAPIService externalAPIService;

    // Full list of documents with docId, resourcePath, prompt, expectedOutput
    static final List<ChatTestCase> DOC_CHAT_TEST_CASES = new ArrayList<>();

    static {
        DOC_CHAT_TEST_CASES.add(newCase("1", "static/summary/Alois_Vertrag_Alois Hinterhuber_VERTRAG_Dienstvertrag_15.12.1985.pdf", "What type of contract is this?", "service contract"));
        DOC_CHAT_TEST_CASES.add(newCase("2", "static/summary/Arbeitszeugnis_Hans Probe_ZEUGNIS_Zeugnis_20.11.2014.pdf", "Who is the employee mentioned in the document?", "Herr Probe"));
        DOC_CHAT_TEST_CASES.add(newCase("3", "static/summary/Daniela_Zeugnis_MMA_Daniela Zimmermann_ZEUGNIS_Zeugnis_31.08.2004.pdf", "Which institution issued the certifications?", "Munich Management Academy"));
        DOC_CHAT_TEST_CASES.add(newCase("4", "static/summary/Daniel_Befristeter Traineevertrag_Daniel Zimmermann_VERTRAG_Befristeter Trainee Vertrag_01.01.2007.pdf", "What is the gross annual salary?", "1942"));
        DOC_CHAT_TEST_CASES.add(newCase("5", "static/summary/Datenschutzerklärung_Daniela Zimmermann_DATZ_Datenschutzerklärung_16.03.2007.pdf", "To whom does the document apply?", "Frau Daniela Zimmermann"));
        DOC_CHAT_TEST_CASES.add(newCase("6", "static/summary/DZ_Zwischenzeugnis_Daniel Zimmermann_ZEUGNIS_Zeugnis_14.09.2024.pdf", "How much expectation is for customer satisfaction improvement in a year?", "20"));
        DOC_CHAT_TEST_CASES.add(newCase("7", "static/summary/Versetzung_Daniel Zimmermann_VERS_Unterrichtung Betriebsrat_01.07.2008.pdf", "Who is the employee affected by the transfer?", "Daniel Zimmermann"));

        // BEWERBUNG
        DOC_CHAT_TEST_CASES.add(newCase("8", "static/summary/mistral_llm_test_docs/BEWERBUNG/ADP_Rademacher_Anschreiben_Johannes-Dietrich Rademacher-Schumann_BEWERBUNG_Anschreiben_01.01.1990.pdf", "What does the applicant enclose with his letter?", "application documents"));
        DOC_CHAT_TEST_CASES.add(newCase("9", "static/summary/mistral_llm_test_docs/BEWERBUNG/AH_Bewerbung komplett_Alois Hinterhuber_BEWERBUNG_Bewerbungsunterlagen_01.07.1985.pdf", "Who is applying for the position?", "Alois Hinterhuber"));
        // Other formats
        DOC_CHAT_TEST_CASES.add(newCase("10", "static/other_format/test_jpeg.jpeg", "What is the document about?", "data protection"));
        DOC_CHAT_TEST_CASES.add(newCase("11", "static/other_format/test_jpg.jpg", "What is the document about?", "data protection"));
        DOC_CHAT_TEST_CASES.add(newCase("12", "static/other_format/test_png.png", "Who is the student named in the document?", "Ms. Dania Zimmermann"));
        DOC_CHAT_TEST_CASES.add(newCase("13", "static/other_format/test_tiff.tif", "Does the document include a German-language TIFF example?", "Yes"));
        DOC_CHAT_TEST_CASES.add(newCase("14", "static/other_format/test_tiff.tif", "Ist dieses Dokument auf Deutsch?", "Ja"));
        DOC_CHAT_TEST_CASES.add(newCase("15", "static/other_format/test_tiff.tif", "Wann ist der Tag des Drachenfests?", "enthält diese"));
        DOC_CHAT_TEST_CASES.add(newCase("16", "static/other_format/test_tiff.tif", "When is the Diwali festival celebrated?", "does not"));

    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getChatTestCases")
    void testChatProcess(ChatTestCase testCase) {
        Resource resource = new ClassPathResource(testCase.getResourcePath());
        Mockito.when(externalAPIService.getFileResource(testCase.getDocId()))
                .thenReturn(resource);

        var result = docSummaryService.chatProcess(testCase.getDocId(), testCase.getPrompt());

        log.info("DocId: {}, Path: {}, Prompt: {}, Result: {}",
                testCase.getDocId(), testCase.getResourcePath(), testCase.getPrompt(), result);

        assertThat(result.toString())
                .as("Checking chat response for docId=%s and path=%s",
                        testCase.getDocId(), testCase.getResourcePath())
                .containsIgnoringCase(testCase.getExpectedOutput());

    }

    private static ChatTestCase newCase(String id, String path, String prompt, String expected) {
        return ChatTestCase.builder()
                .docId(id)
                .resourcePath(path)
                .prompt(prompt)
                .expectedOutput(expected)
                .build();
    }

    @Data
    @Builder
    private static class ChatTestCase {
        private String docId;
        private String resourcePath;
        private String prompt;
        private String expectedOutput;
    }

    public static Stream<ChatTestCase> getChatTestCases() {
        return DOC_CHAT_TEST_CASES.stream();
    }
}
