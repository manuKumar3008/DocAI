package com.bsg.ai.services.qa;

import java.util.List;

/**
 * @param id               short display name shown in the parameterised test output
 * @param question         the question sent to the Q&A endpoint
 * @param expectedInAnswer a keyword or phrase that a correct answer must contain
 *                         (matched case-insensitively)
 */
 public record TestCase(String id, String question, List<String> expectedInAnswer, String documentId) {
}
