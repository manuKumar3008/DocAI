package com.bsg.ai.services.extraction.qa.records;
import java.util.List;

public record QuestionRequest(String question, List<String> documentIds, String conversationId) {
}
