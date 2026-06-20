package com.itsupport.config;

import com.itsupport.service.KnowledgeBase;
import com.itsupport.service.KnowledgeBaseProvider;
import com.itsupport.service.KnowledgeBaseRedis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Autoconfiguration for KnowledgeBase implementation selection.
 * <p>
 * Configure via application.yml:
 *   kb.implementation: redis   # Use Redis vector store
 *   kb.implementation: memory  # Use in-memory PoC (default)
 */
@Configuration
@Slf4j
public class KnowledgeBaseAutoConfig {

    /**
     * Redis-backed implementation (production)
     * <p>
     * ✅ Fixed: Removed EmbeddingModel parameter - KnowledgeBaseRedis no longer needs it
     *     (Spring AI VectorStore handles embedding internally via autoconfiguration)
     */
    @Bean
    @ConditionalOnProperty(name = "kb.implementation", havingValue = "redis")
    public KnowledgeBaseProvider redisKnowledgeBase(VectorStore vectorStore) {
        log.info("KB implementation: Redis vector store");
        return new KnowledgeBaseRedis(vectorStore);
    }

    /**
     * In-memory implementation (PoC / development)
     * <p>
     * Activated when:
     * - kb.implementation=memory, OR
     * - "kb.implementation" property is not set (matchIfMissing = true)
     */
    @Bean
    @ConditionalOnProperty(name = "kb.implementation", havingValue = "memory", matchIfMissing = true)
    public KnowledgeBaseProvider memoryKnowledgeBase() {
        return new KnowledgeBase();
    }
}