package com.bsg.ai.services.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ApplicationPrompt {

    @Getter
    @Value("${prompt.system}")
    private String systemPrompt;

    @Getter
    @Value("${prompt.system.doc.classification}")
    private String docClassificationPrompt;

    @Getter
    @Value("${prompt.system.doc.summary}")
    private String docSummaryPrompt;

    @Getter
    @Value("${prompt.system.doc.summary.of.summaries}")
    private String docSummaryOfSummariesPrompt;

    @Getter
    @Value("${prompt.user.doc.summary.of.summaries}")
    private String docSummaryOfSummariesUserPrompt;

    @Getter
    @Value("${prompt.system.doc.translation}")
    private String docTranslationPrompt;

    @Getter
    @Value("${prompt.system.doc.classification.response.format}")
    private String docClassificationPromptResponseFormat;

    @Getter
    @Value("${prompt.system.doc.processing}")
    private String docProcessingPrompt;

    @Getter
    @Value("${prompt.system.doc.processing.response.format}")
    private String docProcessingPromptResponseFormat;

    @Getter
    @Value("${prompt.system.doc.chat}")
    private String docSystemChatPrompt;

    @Getter
    @Value("${prompt.user.doc.chat}")
    private String docUserChatPrompt;

    @Getter
    @Value("${prompt.system.doc.chat.with.references}")
    private String docSystemChatWithReferencesPrompt;

    public String getSystemPrompt(Map<String, String> dataMapper) {
        String resolvedPrompt = systemPrompt;
        for (Map.Entry<String, String> entry : dataMapper.entrySet()) {
            resolvedPrompt = resolvedPrompt.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return resolvedPrompt;
    }
    @Getter
    @Value("${prompt.system.qna.clarifying-followup}")
    private String qnaClarifyingFollowupPrompt;
    @Getter
    @Value("${prompt.system.qna.language-detection}")
     private String qnaLanguageDetectionPrompt;
}