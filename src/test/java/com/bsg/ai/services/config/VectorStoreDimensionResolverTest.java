package com.bsg.ai.services.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import static org.assertj.core.api.Assertions.assertThat;

class VectorStoreDimensionResolverTest {

    @Test
    void usesEmbeddingModelDimensionsWhenAvailable() {
        EmbeddingModel embeddingModel = new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                return null;
            }

            @Override
            public float[] embed(Document document) {
                return new float[768];
            }

            @Override
            public int dimensions() {
                return 768;
            }
        };

        assertThat(VectorStoreDimensionResolver.resolveDimensions(embeddingModel, 1024)).isEqualTo(768);
    }

    @Test
    void fallsBackToConfiguredDimensionsWhenModelDoesNotExposeThem() {
        EmbeddingModel embeddingModel = new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                return null;
            }

            @Override
            public float[] embed(Document document) {
                return new float[0];
            }

            @Override
            public int dimensions() {
                return 0;
            }
        };

        assertThat(VectorStoreDimensionResolver.resolveDimensions(embeddingModel, 1024)).isEqualTo(1024);
    }
}
