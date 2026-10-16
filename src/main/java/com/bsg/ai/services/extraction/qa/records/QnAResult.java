package com.bsg.ai.services.extraction.qa.records;

import java.util.List;

public record QnAResult(
        String question,
        String answer,
        List<String> sources   // which chunks the answer was built from
) {}
