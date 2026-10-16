package com.bsg.ai.services.extraction.services.records;

import java.awt.image.BufferedImage;

public record PositionedImage(BufferedImage image, float x, float y, float width, float height) {}
