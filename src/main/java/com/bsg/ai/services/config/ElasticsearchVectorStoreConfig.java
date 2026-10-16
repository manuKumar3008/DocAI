package com.bsg.ai.services.config;

import com.bsg.ai.services.extraction.qa.services.HybridElasticsearchVectorStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchVectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchVectorStoreConfig.class);

    private final int fallbackDimensions;
    private final String indexName;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ElasticsearchVectorStoreConfig(
            @Value("${spring.ai.vectorstore.elasticsearch.dimensions:0}") int fallbackDimensions,
            @Value("${spring.ai.vectorstore.elasticsearch.index-name:custom-index-v1}") String indexName) {
        this.fallbackDimensions = fallbackDimensions;
        this.indexName = indexName;
    }

    @Bean
    public HybridElasticsearchVectorStore vectorStore(RestClient restClient, EmbeddingModel embeddingModel) {
        int targetDimensions = VectorStoreDimensionResolver.resolveDimensions(embeddingModel, fallbackDimensions);
        ensureCompatibleIndex(restClient, targetDimensions);

        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setIndexName(indexName);
        options.setDimensions(targetDimensions);
        options.setSimilarity(SimilarityFunction.cosine);
        options.setEmbeddingFieldName("textVector");

        return HybridElasticsearchVectorStore.builder(restClient, embeddingModel)
                .options(options)
                .initializeSchema(true)
                .fuzzyBm25(true)
                .rankWindowSize(10)
                .rankConstant(60)
                .minScore(0.00)
                .build();
    }

    private void ensureCompatibleIndex(RestClient restClient, int targetDimensions) {
        if (targetDimensions <= 0) {
            return;
        }

        try {
            Request request = new Request("GET", "/" + indexName + "/_mapping");
            Response response = restClient.performRequest(request);
            String body = EntityUtils.toString(response.getEntity());
            JsonNode root = objectMapper.readTree(body);
            JsonNode textVectorMapping = root.path(indexName).path("mappings").path("properties").path("textVector");
            JsonNode dimsNode = textVectorMapping.path("dims");

            if (dimsNode.isMissingNode() || dimsNode.isNull() || dimsNode.asInt(0) <= 0) {
                return;
            }

            int existingDimensions = dimsNode.asInt();
            if (existingDimensions != targetDimensions) {
                log.warn("Recreating Elasticsearch index '{}' because textVector dimensions {} do not match target {}",
                        indexName, existingDimensions, targetDimensions);
                deleteIndexIfExists(restClient, indexName);
            }
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return;
            }
            throw new IllegalStateException("Failed to inspect Elasticsearch index mapping for '" + indexName + "'", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inspect Elasticsearch index mapping for '" + indexName + "'", e);
        }
    }

    private void deleteIndexIfExists(RestClient restClient, String indexName) {
        try {
            Request deleteRequest = new Request("DELETE", "/" + indexName);
            restClient.performRequest(deleteRequest);
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() != 404) {
                throw new IllegalStateException("Failed to delete Elasticsearch index '" + indexName + "'", e);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete Elasticsearch index '" + indexName + "'", e);
        }
    }
}
