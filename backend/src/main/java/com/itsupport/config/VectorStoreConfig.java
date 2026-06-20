package com.itsupport.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.RedisClient;

@Configuration
public class VectorStoreConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:#{null}}")
    private String redisPassword;

    @Bean
    public RedisClient redisClient() {
        if (redisPassword != null && !redisPassword.isBlank()) {
            return RedisClient.create(redisHost, redisPort, redisPassword, "");
        }
        return RedisClient.create(redisHost, redisPort);
    }

    @Bean
    public RedisVectorStore vectorStore(RedisClient redisClient, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(redisClient, embeddingModel)
                .indexName("it-support-tickets")
                .initializeSchema(true)
                .metadataFields(
                        MetadataField.tag("category"),
                        MetadataField.tag("id"),
                        MetadataField.text("issue"),
                        MetadataField.text("resolution"),
                        MetadataField.text("keywords")
                )
                .build();
    }
}
