package com.jdh.rag.adapter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * rag_chunks 테이블 JPA 엔티티.
 *
 * fts_vector 는 PostgreSQL GENERATED 컬럼이므로 JPA 매핑에서 제외.
 * metadata 는 JSONB 컬럼이며, JsonbConverter 를 통해 String ↔ PGobject 변환.
 */
@Entity
@Table(name = "rag_chunks")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagChunkEntity {

    @Id
    @Column(name = "chunk_id")
    private String chunkId;

    @Column(name = "doc_id", nullable = false)
    private String docId;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "domain")
    private String domain;

    @Column(name = "version")
    private String version;

    @Column(name = "source")
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * JSONB 컬럼. JsonbConverter 로 String(JSON) ↔ jsonb 변환.
     * insertable/updatable 모두 true (upsert 시 갱신 대상).
     */
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @jakarta.persistence.Convert(converter = JsonbConverter.class)
    private java.util.Map<String, Object> metadata;
}
