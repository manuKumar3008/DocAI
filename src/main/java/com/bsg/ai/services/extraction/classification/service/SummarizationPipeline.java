package com.bsg.ai.services.extraction.classification.service;

import com.bsg.ai.services.extraction.classification.pojo.SummaryResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class SummarizationPipeline {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final int maxRetries;
    private final JsonNode formatSchema;

    public SummarizationPipeline(ChatClient.Builder clientBuilder,
                                 ObjectMapper objectMapper,
                                 @Value("${classification.summarization.max-retries:2}") int maxRetries) {

        this.chatClient = clientBuilder.build();
        this.objectMapper = objectMapper;
        this.maxRetries = Math.max(0, maxRetries);

        try {
            this.formatSchema = objectMapper.readTree(buildSchema());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize summarization schema", e);
        }
    }

    public SummaryResult summarize(int groupIndex, int targetTokens, String groupText) {

        if (groupText == null || groupText.isBlank()) {
            return new SummaryResult();
        }

        String promptText = buildPrompt(groupIndex, targetTokens, groupText);

        int attempt = 0;

        while (attempt <= maxRetries) {

            try {

                String response = callModel(promptText);

                if (response == null || response.isBlank()) {
                    throw new IllegalStateException("Empty response from model");
                }

                return objectMapper.readValue(
                        stripCodeFence(response),
                        SummaryResult.class);

            } catch (Exception e) {

                attempt++;

                log.warn(
                        "Summarization attempt {}/{} failed for group {}: {}",
                        attempt,
                        maxRetries + 1,
                        groupIndex,
                        e.getMessage());

                if (attempt > maxRetries) {
                    throw new IllegalStateException(
                            "Failed to summarize group " + groupIndex,
                            e);
                }
            }
        }

        throw new IllegalStateException("Unexpected failure while summarizing");
    }

    /**
     * Uses Spring AI ChatClient (Mistral).
     */
    private String callModel(String promptText) {

        return Optional.ofNullable(

                chatClient.prompt()
                        .user(promptText)
                        .call()
                        .content()

        ).orElse("");
    }

    private String stripCodeFence(String response) {

        String cleaned = response;

        if (cleaned.contains("```json")) {
            cleaned = cleaned.replace("```json", "");
        }

        if (cleaned.contains("```")) {
            cleaned = cleaned.replace("```", "");
        }

        return cleaned.trim();
    }

    private String buildPrompt(int groupIndex,
                               int targetTokens,
                               String groupText) {

        return """
                You are aggregating multiple summaries into ONE structured, lossless summary.

                CRITICAL RULES:
                - Do NOT omit any numbers, dates, names, or key facts.
                - Preserve ALL quantitative values exactly as written.
                - If multiple values exist, include ALL of them.
                - Do NOT generalize or approximate.
                - Use date/time in ISO 8601 format.
                - INPUT SUMMARIES are provided as a JSON array (strings and/or objects).
                - Avoid duplicate entries in key_entities, dates, numbers, and key_facts.

                FIELD INSTRUCTIONS:
                - key_entities: extract ALL names (people, companies, places, events)
                - dates: include all dates/time references exactly.
                - numbers: include all numeric values (stats, counts, percentages, money, etc.)
                - key_facts: bullet-style important facts (no loss of meaning)
                - compressed_summary: dense paragraph using ALL above info

                MAX LENGTH for compressed_summary: about %d tokens.

                group_index: %d

                INPUT SUMMARIES:
                %s
                """.formatted(targetTokens, groupIndex, groupText);
    }

    private String buildSchema() {

        return """
                {
                  "type": "object",
                  "properties": {
                    "group_index": {
                      "type": "integer"
                    },
                    "key_entities": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "type": {
                            "type": "string"
                          },
                          "value": {
                            "type": "string"
                          }
                        },
                        "required": [
                          "type",
                          "value"
                        ]
                      }
                    },
                    "dates": {
                      "type": "array",
                      "items": {
                        "type": "string"
                      }
                    },
                    "numbers": {
                      "type": "array",
                      "items": {
                        "type": "string"
                      }
                    },
                    "key_facts": {
                      "type": "array",
                      "items": {
                        "type": "string"
                      }
                    },
                    "compressed_summary": {
                      "type": "string"
                    }
                  },
                  "required": [
                    "group_index",
                    "key_entities",
                    "dates",
                    "numbers",
                    "key_facts",
                    "compressed_summary"
                  ]
                }
                """;
    }
}