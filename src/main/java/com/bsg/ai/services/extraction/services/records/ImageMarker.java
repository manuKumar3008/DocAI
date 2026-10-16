package com.bsg.ai.services.extraction.services.records;

public record ImageMarker(
        int imageIndex,
        int pageNumber,
        float pageX,
        float pageY,
        float renderedWidth,
        float renderedHeight
) {}
