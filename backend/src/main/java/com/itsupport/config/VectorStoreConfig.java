package com.itsupport.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

@Configuration
public class VectorStoreConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:#{null}}")
    private String redisPassword;

    @Bean
    public JedisPooled jedisPooled() {
        if (redisPassword != null && !redisPassword.isBlank()) {
            return new JedisPooled(redisHost, redisPort, null, redisPassword);
        }
        return new JedisPooled(redisHost, redisPort);
    }

    @Bean
    public RedisVectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("it-support-tickets")
                .initializeSchema(true)
                .metadataFields(
                        MetadataField.tag("category"),  // for search(query, category)
                        MetadataField.tag("id"),        // for getById()
                        MetadataField.text("issue"),       // ✅ add
                        MetadataField.text("resolution"),  // ✅ add
                        MetadataField.text("keywords")     // ✅ add
                )
                .build();
    }
}
