# springboot-rag

Spring Boot 기반 사내 문서 QA 시스템.
문서를 수집(Ingestion)하고 하이브리드 검색(BM25 + Vector)으로 관련 문서를 찾아 LLM에 전달해 답변을 생성한다.
입출력 소프트 가드레일로 프롬프트 인젝션과 환각(Hallucination)을 방어한다.

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language / Runtime | Java 25 |
| Framework | Spring Boot 4.0.5 |
| AI | Spring AI 2.0.0-M4 (OpenAI Chat, PGVector) |
| 키워드 검색 | pg_search (ParadeDB BM25) |
| 벡터 검색 | Spring AI PGVector (Cosine) |
| DB | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| 임베딩 | Douzone 사내 API (openai 전환 가능) |
| LLM / 가드레일 | OpenAI gpt-4o-mini |
| 문서 파싱 | Apache Tika |
| 테스트 | JUnit 5 + Mockito + MockMvc |
| 기타 | Lombok, Jackson 3.x |

---

## 아키텍처

헥사고날 아키텍처(Ports & Adapters) 기반으로 설계되어 있다.

```
controller/   ← HTTP 인바운드 어댑터
service/      ← 비즈니스 로직 (도메인 오케스트레이션)
port/         ← 추상화 인터페이스 (서비스가 의존)
adapter/      ← 포트 구현체 (DB, 외부 API)
domain/       ← 순수 도메인 모델 (record)
support/      ← 유틸리티 (ContextBuilder, RrfRankFusion, SearchLogger)
exception/    ← 예외 계층 (RagException, 서비스별 예외, GlobalAdvice)
config/       ← Spring 설정
```

### RAG 답변 파이프라인

```
HTTP 요청
  └─ RagAnswerService.answer(RagAnswerRequest)
       ├─ 1) InputGuardrailPort   ← 프롬프트 인젝션 차단
       ├─ 2) QueryPreprocessPort  ← keywordQuery(BM25) + vectorQuery(HyDE) 생성
       ├─ 3) HybridSearchService  ← BM25(keywordQuery) + Vector(vectorQuery) + RRF + Rerank
       ├─ 4) ContextBuilder       ← dedup / trim / sanitize
       ├─ 5) ChatClient           ← OpenAI gpt-4o-mini 호출
       └─ 6) OutputGuardrailPort  ← 환각 감지 / 근거 검증
```

---

## 시작하기

### 사전 요구사항

- Java 25
- Docker & Docker Compose
- OpenAI API Key

### 환경변수 설정

| 변수 | 설명 | 필수 |
|---|---|---|
| `OPENAI_API_KEY` | OpenAI API 키 (Chat LLM + 가드레일) | ✅ |
| `DOUZONE_EMBEDDING_URL` | Douzone 사내 임베딩 API URL | ✅ |
| `DB_PASSWORD` | DB 패스워드 (미설정 시 `ragpass`) | - |

### 로컬 실행

```bash
# 1. PostgreSQL + pg_search(ParadeDB) 컨테이너 시작
docker compose up -d

# 2. 환경변수 설정
export OPENAI_API_KEY=sk-...
export DOUZONE_EMBEDDING_URL=https://...

# 3. 로컬 프로파일로 실행 (in-memory, 가드레일/전처리 비활성화, 스키마 자동 초기화)
./gradlew bootRun --args='--spring.profiles.active=local'

# 운영 설정 그대로 실행 (DB, 가드레일, LLM 전처리 모두 활성화)
./gradlew bootRun
```

### 테스트 실행

```bash
./gradlew test
```

---

## API

### 문서 수집 (Ingestion)

```
POST /api/ingest/text
Content-Type: application/json

{
  "docId":    "doc-001",
  "source":   "규정집.pdf",
  "domain":   "hr",
  "version":  "2024.01",
  "tenantId": "default",
  "content":  "문서 내용..."
}
```

```
POST /api/ingest/file
Content-Type: multipart/form-data

file=@규정집.pdf  docId=doc-001  source=규정집.pdf  domain=hr  version=2024.01
```

### 문서 관리 (Document Management)

```
GET    /api/documents?tenantId=default&domain=hr   ← 문서 목록 조회
GET    /api/documents/{docId}                       ← 특정 문서 정보 (없으면 404)
DELETE /api/documents/{docId}                       ← rag_chunks + vector_store 동시 삭제 → 204
```

> 문서 갱신은 `DELETE` 후 `/api/ingest/*` 재호출로 처리한다.
> 인제스트 중 키워드 색인이 실패하면, 직전에 저장된 벡터 데이터도 즉시 롤백해 반쪽 저장 상태를 남기지 않는다.

### 질의응답

```
POST /api/rag/answer
Content-Type: application/json

{
  "query":        "연차 신청 방법이 궁금합니다",
  "tenantId":     "default",
  "domain":       "hr",
  "sortByLatest": false
}
```

**응답 예시**

```json
{
  "requestId": "550e8400-...",
  "answer": "연차 신청은 [S1]에 따라 사내 포털에서 ...",
  "citations": [
    {
      "citeKey":  "S1",
      "docId":    "doc-001",
      "source":   "규정집.pdf",
      "snippet":  "연차 신청은 사내 포털에서..."
    }
  ]
}
```

### 스트리밍 답변 (SSE)

```
POST /api/rag/answer/stream
Content-Type: application/json
Accept: text/event-stream

{ "query": "연차 신청 방법", "tenantId": "default", "domain": "hr" }
```

이벤트 타입:

| 이벤트 | 내용 |
|---|---|
| `token` | LLM이 생성하는 토큰 조각 (실시간 전송) |
| `done` | 최종 requestId + citations JSON |
| `error` | 에러 발생 시 메시지 |

> 스트리밍은 입력 가드레일 적용, 출력 가드레일은 생략(완전한 답변이 필요).

### 피드백 수집

```
PATCH /api/rag/feedback/{requestId}
Content-Type: application/json

{ "accepted": true }
```

`search_logs.answer_accepted` 를 업데이트한다. cosine threshold 튜닝에 사용되는 핵심 신호다.

### 검색 장애 응답 정책

- 한쪽 검색 채널만 실패하면 남은 채널 결과로 계속 처리한다.
- 키워드 검색과 벡터 검색이 모두 실패하면 일반 fallback 답변으로 숨기지 않고 `503 Service Unavailable`과 `S0001`을 반환한다.

### 검색 품질 분석

```
GET /api/analytics/search?from=2026-04-01T00:00:00Z&to=2026-04-07T23:59:59Z
```

`from/to` 미입력 시 최근 7일 기준.

**응답 예시**

```json
{
  "totalRequests": 1024,
  "feedbackCount": 320,
  "overallAcceptanceRate": 0.84,
  "scoreBuckets": [
    { "scoreLow": 0.6, "scoreHigh": 0.7, "total": 120, "acceptanceRate": 0.72 },
    { "scoreLow": 0.7, "scoreHigh": 0.8, "total": 280, "acceptanceRate": 0.88 }
  ],
  "channelStats": [
    { "channel": "fused",   "count": 512, "avgScore": 0.74 },
    { "channel": "lexical", "count": 256, "avgScore": 0.65 }
  ],
  "dailyStats": [
    { "date": "2026-04-01", "requestCount": 148 }
  ]
}
```

`scoreBuckets` 의 `acceptanceRate` 를 보고 수락률이 낮은 점수 구간을 파악해 `rag.vector-threshold` 를 조정한다.

---

## 주요 설정

`src/main/resources/application.yaml`

| 프로퍼티 | 기본값 | 설명 |
|---|---|---|
| `rag.embedding.type` | `douzone` | 임베딩 모델 선택 (`douzone` \| `openai`) |
| `rag.keyword-search-type` | `postgres` | 키워드 검색 어댑터 (`postgres` \| `memory`) |
| `rag.guardrail.enabled` | `true` | 소프트 가드레일 활성화 여부 |
| `rag.query-preprocess.enabled` | `true` | 쿼리 전처리 활성화 여부 |
| `rag.top-k-final` | `5` | 최종 검색 결과 수 |
| `rag.vector-threshold` | `0.6` | 벡터 유사도 하한선 |
| `rag.chunk.size` | `600` | 고정 청킹 크기 (토큰 수) |
| `rag.chunk.strategy` | `semantic` | 청킹 전략 (`semantic` \| `fixed`) |
| `rag.sse-timeout-ms` | `120000` | SSE 스트리밍 타임아웃 (ms) |

#### 청킹 전략 (`rag.chunk.strategy`)

| 전략 | 동작 |
|---|---|
| `semantic` (기본값) | LLM이 구조 여부를 직접 판단. 구조가 있으면 조항·항목 단위로 분리, 없으면 `fixed`로 자동 대체. 텍스트 20,000자 초과·LLM 실패 시에도 `fixed`로 대체. |
| `fixed` | 고정 토큰 크기(`rag.chunk.size`)로 분리 |

> **비용 주의**: `rag.chunk.strategy=semantic`이면 수집 요청당 최대 LLM 호출 1회 추가 발생.

#### 쿼리 전처리

`rag.query-preprocess.enabled=true`(기본값)이면 `LlmQueryPreprocessAdapter`가 동작하며 LLM 호출 1회가 추가된다.

| 출력 | 설명 | 사용 채널 |
|---|---|---|
| `keywordQuery` | 핵심 명사·동사 중심 쿼리 | BM25 검색 |
| `vectorQuery` | 이상적 답변을 서술한 가상 문서 (HyDE) | Vector 검색 |

> **임베딩 모델 전환 시 주의**: `rag.embedding.type` 변경 시 `vector_store` 테이블 DROP 후 문서 전체 재수집 필요.

---

## 에러 코드

| 코드 | HTTP | 설명 |
|---|---|---|
| `R0001` | 500 | 내부 서버 오류 |
| `R0002` | 400 | 잘못된 요청 |
| `R0003` | 404 | 리소스 없음 |
| `R0004` | 405 | 허용되지 않는 HTTP 메서드 |
| `D0001` | 404 | 존재하지 않는 문서 |
| `I0001` | 500 | 문서 수집 실패 |
| `I0002` | 422 | 파일 파싱 실패 |
| `I0003` | 400 | 수집할 문서 내용 없음 |
| `I0004` | 503 | 벡터 저장소 저장 실패 |
| `S0001` | 503 | 검색 서비스 사용 불가 |
| `L0001` | 503 | AI 답변 생성 실패 |
| `L0002` | 503 | 임베딩 처리 실패 |
