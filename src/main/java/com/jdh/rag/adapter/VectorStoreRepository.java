package com.jdh.rag.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring AI가 관리하는 vector_store 테이블에서 docId로 레코드 ID를 조회한다.
 * JPA 엔티티 충돌을 피하기 위해 JdbcTemplate 네이티브 쿼리를 사용한다.
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.keyword-search-type", havingValue = "postgres")
public class VectorStoreRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * metadata JSONB 컬럼에서 docId 가 일치하는 레코드의 UUID 목록을 반환한다.
     * VectorStore.delete(ids) 호출 시 필요하다.
     */
    public List<String> findIdsByDocId(String docId) {
        return jdbcTemplate.queryForList(
                "SELECT id::text FROM vector_store WHERE metadata->>'docId' = ?",
                String.class, docId
        );
    }
}
