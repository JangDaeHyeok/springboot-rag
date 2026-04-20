package com.jdh.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 컨텍스트 로드 스모크 테스트.
 * DB / OpenAI / Milvus 없이도 실행되도록 인메모리 어댑터 + 더미 API 키 사용.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "rag.keyword-search-type=memory",
        "rag.vector-store-type=memory",                // SimpleVectorStore(인메모리) 사용, Milvus 비활성화
        "spring.ai.openai.api-key=test-key",
        "spring.datasource.url=",                      // 데이터소스 비활성화
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
                "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration," +
                "org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration",
        "spring.sql.init.mode=never"
})
class RagApplicationTests {

    @Test
    @DisplayName("Spring 컨텍스트 정상 로드")
    void contextLoads() {
    }
}
