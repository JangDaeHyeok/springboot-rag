package com.jdh.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableConfigurationProperties({RagProperties.class, DouzoneEmbeddingProperties.class})
public class AiConfig {

    /**
     * ChatClient - spring-ai-starter-model-openai 가 OpenAI 기반으로 자동 구성한다.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * SimpleVectorStore (인메모리) - 데이터소스 없이 실행할 때 폴백.
     * spring-ai-starter-vector-store-pgvector + 데이터소스가 있으면
     * PgVectorStore 가 먼저 등록되므로 이 빈은 생성되지 않는다.
     */
    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
