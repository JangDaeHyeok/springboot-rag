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
  └─ RagAnswerService
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

# 3. 애플리케이션 실행
./gradlew bootRun

# DB 없이 빠르게 테스트 (in-memory 모드)
./gradlew bootRun --args='--rag.keyword-search-type=memory --rag.guardrail.enabled=false'
```

### 테스트 실행

```bash
./gradlew test
```

---

## API

### 문서 수집

```
POST /api/ingest
Content-Type: application/json

{
  "docId": "doc-001",
  "content": "문서 내용",
  "metadata": { "domain": "hr", "source": "규정집.pdf" }
}
```

```
POST /api/ingest/file
Content-Type: multipart/form-data

file=@규정집.pdf
docId=doc-001
```

### 질의응답

```
POST /api/rag/answer
Content-Type: application/json

{
  "query": "연차 신청 방법이 궁금합니다",
  "filters": { "domain": "hr" }
}
```

**응답 예시**

```json
{
  "requestId": "550e8400-...",
  "answer": "연차 신청은 [S1]에 따라 사내 포털에서 ...",
  "citations": [
    {
      "citeKey": "S1",
      "docId": "doc-001",
      "source": "규정집.pdf",
      "snippet": "연차 신청은 사내 포털에서..."
    }
  ]
}
```

---

## 주요 설정

`src/main/resources/application.yaml`

| 프로퍼티 | 기본값 | 설명 |
|---|---|---|
| `rag.embedding.type` | `douzone` | 임베딩 모델 선택 (`douzone` \| `openai`) |
| `rag.keyword-search-type` | `postgres` | 키워드 검색 어댑터 (`postgres` \| `memory`) |
| `rag.guardrail.enabled` | `true` | 소프트 가드레일 활성화 여부 |
| `rag.top-k-final` | `5` | 최종 검색 결과 수 |
| `rag.vector-threshold` | `0.6` | 벡터 유사도 하한선 |
| `rag.chunk.size` | `600` | 고정 청킹 크기 (토큰 수) |
| `rag.chunk.strategy` | `semantic` | 청킹 전략 (`semantic` \| `fixed`) |

#### 청킹 전략 (`rag.chunk.strategy`)

| 전략 | 동작 |
|---|---|
| `semantic` (기본값) | LLM이 구조 여부를 직접 판단. 구조가 있으면 조항·항목 단위로 분리, 없으면 `fixed`로 자동 대체. 텍스트 20,000자 초과·LLM 실패 시에도 `fixed`로 대체. |
| `fixed` | 고정 토큰 크기(`rag.chunk.size`)로 분리 |

> **비용 주의**: `rag.chunk.strategy=semantic`이면 수집 요청당 최대 LLM 호출 1회 추가 발생.

#### 쿼리 전처리

답변 요청마다 `LlmQueryPreprocessAdapter`가 자동으로 동작하며 LLM 호출 1회가 추가된다.

| 출력 | 설명 | 사용 채널 |
|---|---|---|
| `keywordQuery` | 구어체·조사를 제거한 핵심 명사·동사 중심 쿼리 | BM25 검색 |
| `vectorQuery` | 질문의 이상적 답변을 서술한 가상 문서 (HyDE 기법) | Vector 검색 |

LLM 호출 실패 또는 파싱 실패 시 원문 쿼리를 그대로 사용하므로(fail-open) 메인 파이프라인은 차단되지 않는다.

> **임베딩 모델 전환 시 주의**: `rag.embedding.type` 변경 시 벡터 공간이 달라지므로 `vector_store` 테이블 DROP 후 문서 전체 재수집 필요.

---

## 에러 코드

| 코드 | HTTP | 설명 |
|---|---|---|
| `R0001` | 500 | 내부 서버 오류 |
| `R0002` | 400 | 잘못된 요청 |
| `R0003` | 404 | 리소스 없음 |
| `I0001` | 400 | 유효하지 않은 수집 요청 |
| `I0002` | 422 | 파일 파싱 실패 |
| `I0003` | 400 | 수집할 문서 내용 없음 |
| `I0004` | 503 | 벡터 저장소 저장 실패 |
| `S0001` | 503 | 검색 서비스 사용 불가 |
| `L0001` | 503 | AI 답변 생성 실패 |
| `L0002` | 503 | 임베딩 처리 실패 |
