package com.bsg.ai.services.extraction.summary.constant;

import org.apache.commons.text.WordUtils;

public enum SummaryType {
    LONG, SHORT;

    public String getDisplayName() {
        return WordUtils.capitalizeFully(name().replace("_", " "));
    }
}
