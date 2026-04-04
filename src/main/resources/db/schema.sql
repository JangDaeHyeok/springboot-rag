-- ────────────────────────────────────────────────────────────────────────────
-- RAG DB 스키마
--
-- vector_store : Spring AI PGVector가 자동 생성 (initialize-schema=true)
-- rag_chunks   : BM25 키워드 검색용 (pg_search). 인제스트 시 직접 INSERT.
-- search_logs  : cosine threshold 튜닝 및 평가 데이터 적재.
-- ────────────────────────────────────────────────────────────────────────────

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_search;

-- ── rag_chunks ───────────────────────────────────────────────────────────────
-- pg_search(ParadeDB) BM25 기반 키워드 검색 테이블.
-- fts_vector GENERATED 컬럼 제거 → BM25 인덱스(idx_rag_chunks_bm25)가 대체.
-- metadata   : 기타 메타데이터 전체 (JSONB).
CREATE TABLE IF NOT EXISTS rag_chunks (
    chunk_id   TEXT        PRIMARY KEY,
    doc_id     TEXT        NOT NULL,
    content    TEXT        NOT NULL,
    tenant_id  TEXT,
    domain     TEXT,
    version    TEXT,
    source     TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata   JSONB       NOT NULL DEFAULT '{}'
);

-- pg_search BM25 인덱스.
-- key_field : PRIMARY KEY 컬럼 지정 필수.
-- text_fields: BM25 스코어링 대상 컬럼. 한국어·영문 혼용 시 tokenizer를 "icu"로 변경 가능.
CREATE INDEX IF NOT EXISTS idx_rag_chunks_bm25
    ON rag_chunks
    USING bm25 (chunk_id, content)
    WITH (
        key_field   = 'chunk_id',
        text_fields = '{"content": {"tokenizer": {"type": "default"}}}'
    );

CREATE INDEX IF NOT EXISTS idx_rag_chunks_tenant
    ON rag_chunks(tenant_id, domain);

CREATE INDEX IF NOT EXISTS idx_rag_chunks_doc
    ON rag_chunks(doc_id);

-- ── search_logs ───────────────────────────────────────────────────────────────
-- 하이브리드 검색 결과 로그. cosine threshold 튜닝에 사용.
--
-- 튜닝 쿼리 예시:
--   SELECT FLOOR(cosine_score * 10) / 10 AS bucket,
--          COUNT(*) AS total,
--          SUM(CASE WHEN answer_accepted = false THEN 1 ELSE 0 END)::DECIMAL / COUNT(*) AS failure_rate
--   FROM search_logs
--   GROUP BY bucket ORDER BY bucket;
CREATE TABLE IF NOT EXISTS search_logs (
    id              BIGSERIAL   PRIMARY KEY,
    request_id      TEXT        NOT NULL,
    query           TEXT        NOT NULL,
    doc_id          TEXT,
    chunk_id        TEXT,
    cosine_score    DOUBLE PRECISION,   -- RRF 결합 점수 (원 cosine 은 추후 개선)
    rank_pos        INT,
    used_in_prompt  BOOLEAN     NOT NULL DEFAULT false,
    answer_accepted BOOLEAN,            -- 사용자 피드백 (null = 미수집)
    channel         TEXT,               -- 'lexical' | 'vector' | 'fused'
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_search_logs_request
    ON search_logs(request_id);

CREATE INDEX IF NOT EXISTS idx_search_logs_created
    ON search_logs(created_at);
