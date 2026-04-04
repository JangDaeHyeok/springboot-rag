package com.jdh.rag.adapter;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * search_logs 테이블 JPA 엔티티.
 * cosine threshold 튜닝 및 평가 데이터 적재용.
 */
@Entity
@Table(name = "search_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "query", nullable = false, columnDefinition = "text")
    private String query;

    @Column(name = "doc_id")
    private String docId;

    @Column(name = "chunk_id")
    private String chunkId;

    /** RRF 결합 점수 (원 cosine 은 VectorStore 직접 쿼리로 추후 개선 가능) */
    @Column(name = "cosine_score")
    private Double cosineScore;

    @Column(name = "rank_pos")
    private Integer rankPos;

    @Column(name = "used_in_prompt", nullable = false)
    private boolean usedInPrompt;

    /** 사용자 피드백 (null = 미수집) */
    @Column(name = "answer_accepted")
    private Boolean answerAccepted;

    /** "lexical" | "vector" | "fused" */
    @Column(name = "channel")
    private String channel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
