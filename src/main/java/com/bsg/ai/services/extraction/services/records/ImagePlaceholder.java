package com.bsg.ai.services.extraction.services.records;

import java.awt.image.BufferedImage;

public record ImagePlaceholder(
        int imageIndex,
        int pageNumber,
        BufferedImage bufferedImage,
        float pageX,
        float pageY,
        float renderedWidth,
        float renderedHeight,
        int pixelWidth,
        int pixelHeight
) {}
