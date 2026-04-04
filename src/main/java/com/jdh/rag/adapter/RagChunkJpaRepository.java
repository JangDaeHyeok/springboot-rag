package com.jdh.rag.adapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * rag_chunks 테이블 레포지토리.
 *
 * <p>키워드 검색은 pg_search(ParadeDB) BM25 인덱스를 네이티브 쿼리로 사용한다.
 * <ul>
 *   <li>{@code @@@} 연산자: BM25 인덱스 검색</li>
 *   <li>{@code paradedb.match(field, query)}: 지정 필드 매칭 쿼리 생성</li>
 *   <li>{@code paradedb.score(key_field)}: BM25 관련도 점수 반환</li>
 * </ul>
 * 동적 필터(tenantId, domain)는 {@code (:param IS NULL OR col = :param)} 패턴으로 처리한다.
 */
public interface RagChunkJpaRepository extends JpaRepository<RagChunkEntity, String> {

    /**
     * pg_search BM25 검색 + 메타데이터 필터.
     *
     * @param query    검색 쿼리 문자열 (paradedb.match 로 변환)
     * @param tenantId 테넌트 필터 (null 이면 전체)
     * @param domain   도메인 필터 (null 이면 전체)
     * @param topN     반환 최대 건수
     * @return [chunk_id, doc_id, content, metadata(jsonb→text), bm25_score] 배열 목록
     */
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
}