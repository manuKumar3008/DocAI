package com.bsg.ai.services.config;

import org.springframework.ai.embedding.EmbeddingModel;

public final class VectorStoreDimensionResolver {

    private VectorStoreDimensionResolver() {
    }

    public static int resolveDimensions(EmbeddingModel embeddingModel, int fallbackDimensions) {
        if (embeddingModel == null) {
            return fallbackDimensions;
        }

        try {
            int dimensions = embeddingModel.dimensions();
            if (dimensions > 0) {
                return dimensions;
            }
        } catch (Exception ignored) {
            // Some embedding implementations may not expose dimensions directly.
        }

        return fallbackDimensions;
    }
}
