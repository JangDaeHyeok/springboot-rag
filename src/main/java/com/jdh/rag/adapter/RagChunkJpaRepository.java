package com.jdh.rag.adapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * rag_chunks 테이블 레포지토리. pg_search(ParadeDB) BM25 검색에 네이티브 쿼리 사용.
 * {@code @@@}: BM25 검색 연산자 / {@code paradedb.match}: 쿼리 생성 / {@code paradedb.score}: BM25 점수.
 * 동적 필터는 {@code :param IS NULL OR col = :param} 패턴.
 */
public interface RagChunkJpaRepository extends JpaRepository<RagChunkEntity, String> {

    /** BM25 검색. 반환: [chunk_id, doc_id, content, metadata(jsonb→text), bm25_score] */
    @Query(value = """
            SELECT
                r.chunk_id,
                r.doc_id,
                r.content,
                r.metadata::text                AS metadata_json,
                paradedb.score(r.chunk_id)      AS score
            FROM rag_chunks r
            WHERE r.chunk_id @@@ paradedb.match('content', :query)
              AND (:tenantId IS NULL OR r.tenant_id = :tenantId)
              AND (:domain   IS NULL OR r.domain    = :domain)
            ORDER BY score DESC
            LIMIT :topN
            """, nativeQuery = true)
    List<Object[]> findByPgSearch(
            @Param("query")    String query,
            @Param("tenantId") String tenantId,
            @Param("domain")   String domain,
            @Param("topN")     int    topN
    );

    boolean existsByDocId(String docId);

    /** docId 별 문서 요약 정보. 반환: [doc_id, source, domain, version, tenant_id, chunk_count, first_created_at] */
    @Query("""
            SELECT r.docId, r.source, r.domain, r.version, r.tenantId, COUNT(r), MIN(r.createdAt)
            FROM RagChunkEntity r
            WHERE (:tenantId IS NULL OR r.tenantId = :tenantId)
              AND (:domain   IS NULL OR r.domain   = :domain)
            GROUP BY r.docId, r.source, r.domain, r.version, r.tenantId
            ORDER BY MIN(r.createdAt) DESC
            """)
    List<Object[]> findDocumentInfos(
            @Param("tenantId") String tenantId,
            @Param("domain")   String domain
    );

    /** 단일 docId의 문서 요약 정보. 반환: [doc_id, source, domain, version, tenant_id, chunk_count, first_created_at] */
    @Query("""
            SELECT r.docId, r.source, r.domain, r.version, r.tenantId, COUNT(r), MIN(r.createdAt)
            FROM RagChunkEntity r
            WHERE r.docId = :docId
            GROUP BY r.docId, r.source, r.domain, r.version, r.tenantId
            """)
    List<Object[]> findDocumentInfoByDocId(@Param("docId") String docId);

    @Modifying
    @Query("DELETE FROM RagChunkEntity r WHERE r.docId = :docId")
    void deleteByDocId(@Param("docId") String docId);
}
