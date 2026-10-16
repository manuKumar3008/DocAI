package com.bsg.ai.services.qa;

import java.util.List;

public record TestResult(String id, String question, List<String> expected, String actual, boolean passed, float score) {}
