package com.jdh.rag.adapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * search_logs 테이블 레포지토리.
 * 기본 save/saveAll 외에 피드백 업데이트, 검색 품질 분석 쿼리를 제공한다.
 */
public interface SearchLogJpaRepository extends JpaRepository<SearchLogEntity, Long> {

    boolean existsByRequestId(String requestId);

    @Modifying
    @Query("UPDATE SearchLogEntity s SET s.answerAccepted = :accepted WHERE s.requestId = :requestId")
    void updateAnswerAccepted(@Param("requestId") String requestId, @Param("accepted") boolean accepted);

    // ── Analytics ─────────────────────────────────────────────────────────────

    /** 기간 내 고유 requestId 수 (전체 요청 건수) */
    @Query(value = "SELECT COUNT(DISTINCT request_id) FROM search_logs WHERE created_at BETWEEN :from AND :to",
            nativeQuery = true)
    long countDistinctRequests(@Param("from") Instant from, @Param("to") Instant to);

    /** 피드백이 있는 고유 requestId 수 */
    @Query(value = """
            SELECT COUNT(DISTINCT request_id)
            FROM search_logs
            WHERE created_at BETWEEN :from AND :to
              AND answer_accepted IS NOT NULL
            """, nativeQuery = true)
    long countFeedbackRequests(@Param("from") Instant from, @Param("to") Instant to);

    /** 전체 답변 수락률. 반환: [acceptance_rate] (null 가능) */
    @Query(value = """
            SELECT SUM(CASE WHEN answer_accepted = true THEN 1 ELSE 0 END)::DECIMAL
                   / NULLIF(COUNT(CASE WHEN answer_accepted IS NOT NULL THEN 1 END), 0)
            FROM search_logs
            WHERE created_at BETWEEN :from AND :to
            """, nativeQuery = true)
    Double findOverallAcceptanceRate(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * 점수 버킷별 통계 (cosine threshold 튜닝용).
     * used_in_prompt=true 인 행만 집계해 실제 LLM에 전달된 청크 기준으로 분석한다.
     * 반환: [score_bucket, total, acceptance_rate]
     */
    @Query(value = """
            SELECT
                FLOOR(cosine_score * 10) / 10            AS score_bucket,
                COUNT(*)                                  AS total,
                SUM(CASE WHEN answer_accepted = true THEN 1 ELSE 0 END)::DECIMAL
                    / NULLIF(COUNT(CASE WHEN answer_accepted IS NOT NULL THEN 1 END), 0) AS acceptance_rate
            FROM search_logs
            WHERE created_at BETWEEN :from AND :to
              AND used_in_prompt = true
              AND cosine_score IS NOT NULL
            GROUP BY score_bucket
            ORDER BY score_bucket
            """, nativeQuery = true)
    List<Object[]> findScoreBuckets(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * 채널(lexical/vector/fused)별 통계.
     * 반환: [channel, count, avg_score]
     */
    @Query(value = """
            SELECT channel, COUNT(*) AS cnt, AVG(cosine_score) AS avg_score
            FROM search_logs
            WHERE created_at BETWEEN :from AND :to
            GROUP BY channel
            ORDER BY cnt DESC
            """, nativeQuery = true)
    List<Object[]> findChannelStats(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * 일별 고유 요청 건수.
     * 반환: [day (DATE), request_count]
     */
    @Query(value = """
            SELECT DATE(created_at) AS day, COUNT(DISTINCT request_id) AS cnt
            FROM search_logs
            WHERE created_at BETWEEN :from AND :to
            GROUP BY day
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> findDailyRequestCounts(@Param("from") Instant from, @Param("to") Instant to);
}