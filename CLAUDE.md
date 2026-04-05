# CLAUDE.md

이 파일은 Claude Code가 프로젝트를 이해하고 코드 리뷰 및 개발을 수행할 때 참고하는 지침 파일입니다.

---

## 프로젝트 개요

**Spring Boot RAG (Retrieval-Augmented Generation) 서비스**

사내 문서 기반 QA 시스템. 문서를 수집(Ingestion)하고 하이브리드 검색(BM25 + Vector)으로 관련 문서를 찾아 LLM에 전달해 답변을 생성한다. 입출력 소프트 가드레일로 프롬프트 인젝션·환각을 방어한다.

### 기술 스택

| 분류 | 기술 |
|---|---|
| Language / Runtime | Java 25 |
| Framework | Spring Boot 4.x |
| AI | Spring AI 2.x (OpenAI Chat, PGVector) |
| 키워드 검색 | pg_search (ParadeDB BM25) |
| 벡터 검색 | Spring AI PGVector (Cosine) |
| DB | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| 임베딩 | Douzone 사내 API (기본값) / OpenAI 전환 가능 |
| LLM / 가드레일 | OpenAI ChatClient (gpt-4o-mini) |
| 문서 파싱 | Apache Tika |
| 테스트 | JUnit 5 + Mockito + MockMvc |
| 기타 | Lombok, Jackson 3.x |

> **OpenAI API 용도 구분**:
> - Chat(LLM): RAG 답변 생성 (`spring.ai.openai.chat`)
> - 가드레일: 입출력 안전 검사 (`OpenAiGuardrailAdapter`) — `OPENAI_API_KEY` 공유
> - Embedding: **사용 안 함** (`spring.ai.openai.embedding.enabled: false`) — Douzone API 사용

---

## 아키텍처 — 헥사고날 (Ports & Adapters)

```
controller/          ← HTTP 인바운드 어댑터
service/             ← 비즈니스 로직 (도메인 오케스트레이션)
port/                ← 추상화 인터페이스 (서비스가 의존)
adapter/             ← 포트 구현체 (DB, 외부 API)
domain/              ← 순수 도메인 모델 (record)
support/             ← 유틸리티 (ContextBuilder, RrfRankFusion, SearchLogger)
exception/           ← 예외 계층 (RagException, 서비스별 예외, GlobalAdvice)
config/              ← Spring 설정
```

### 포트 목록

| 포트 | 역할 | 구현체 (교체 조건) |
|---|---|---|
| `QueryPreprocessPort` | 쿼리 전처리 (BM25·Vector 채널별 최적화) | `LlmQueryPreprocessAdapter` (항상 활성, 조건 없음) |
| `KeywordSearchPort` | BM25 키워드 검색 | `PgKeywordSearchAdapter` / `InMemoryKeywordSearchAdapter` (`rag.keyword-search-type`) |
| `KeywordIndexPort` | 문서 BM25 색인 | 동일 어댑터가 구현 |
| `VectorSearchPort` | Cosine 벡터 검색 | `SpringAiVectorSearchAdapter` |
| `ChunkSplitterPort` | 문서 청킹 | `FixedTokenChunkSplitter`(기본) / `SemanticChunkSplitter` (`rag.chunk.strategy`) |
| `RerankPort` | 검색 결과 재정렬 | `ScoreRerankAdapter` / `DateDescRerankAdapter` — `RerankDispatcher`가 위임 |
| `InputGuardrailPort` | 입력 안전 검사 | `OpenAiGuardrailAdapter` / `NoOpGuardrailAdapter` (`rag.guardrail.enabled`) |
| `OutputGuardrailPort` | 출력 안전 검사 | 동일 어댑터가 구현 |
| `SearchLogPort` | 검색 이력 저장 | `PgSearchLogAdapter` / `NoOpSearchLogAdapter` |

### 핵심 원칙

- **서비스는 포트(인터페이스)에만 의존한다.** 어댑터(구현체)를 직접 참조하지 않는다.
- **도메인 모델(`domain/`)에는 프레임워크 의존성을 넣지 않는다.** 순수 Java record만 허용.
- **어댑터 교체는 `@ConditionalOnProperty`로 제어한다.**
  - `rag.keyword-search-type=postgres|memory`
  - `rag.embedding.type=douzone|openai`
  - `rag.guardrail.enabled=true|false`
  - `rag.chunk.strategy=fixed|semantic`

---

## 예외 처리 구조

```
RagException (base)
├── IngestionException   ← 문서 수집 파이프라인
├── SearchException      ← 검색 불가
└── LlmException         ← LLM/임베딩 호출 실패

RagExceptionAdvice (@RestControllerAdvice)  ← 전역 처리
RagExceptionEnum                            ← HTTP 상태 + 에러 코드 정의
```

에러 코드 체계: `R####` 공통 / `I####` Ingestion / `S####` Search / `L####` LLM

---

## 소프트 가드레일

RAG 파이프라인에 입출력 2단계 안전 검사가 포함된다. `rag.guardrail.enabled=false`이면 `NoOpGuardrailAdapter`가 활성화되어 모든 검사를 PASS로 통과시킨다.

### 입력 가드레일 (`InputGuardrailPort`)

사용자 질의가 검색·LLM 파이프라인에 진입하기 **전** 검사:

| 판정 | 조건 | 동작 |
|---|---|---|
| BLOCK | 프롬프트 인젝션, 역할 변경 시도, 유해 요청 | 검색 없이 즉시 차단 메시지 반환 |
| WARN | 업무 범위 외 질문 | 경고 로그 후 파이프라인 계속 |
| PASS | 정상적인 업무·지식 질문 | 정상 진행 |

### 출력 가드레일 (`OutputGuardrailPort`)

LLM 답변 생성 **후** 사용자에게 반환하기 전 검사:

| 판정 | 조건 | 동작 |
|---|---|---|
| BLOCK | 문서에 없는 내용 단언 (환각), 내용 왜곡 | 차단 메시지 반환 (citations 포함) |
| WARN | 문서 근거 불충분, 불확실 내용 | 답변 끝에 경고 문구 추가 |
| PASS | 문서에 충분히 근거하거나 불확실성 명시 | 정상 반환 |

**Fail-open 정책**: 가드레일 LLM 호출 실패 시 항상 PASS → 메인 파이프라인 차단 없음

---

## 코드 리뷰 기준

코드 리뷰 시 아래 항목을 반드시 점검합니다.

### 보안

- **하드코딩 금지**: API 키, 비밀번호, DB URL 등은 환경변수 또는 `application.yaml`의 `${ENV_VAR}` 패턴으로만 관리한다.
- **SQL 인젝션**: 네이티브 쿼리에서 문자열 접합(`+`) 대신 반드시 `@Param` 바인딩을 사용한다.
- **프롬프트 인젝션**: 외부 문서 내용을 LLM 컨텍스트에 삽입할 때 `ContextBuilder.sanitize()`를 거쳐야 한다. 사용자 쿼리도 직접 system prompt에 포함하지 않는다.
- **민감 정보 로깅 금지**: 쿼리 내용, 사용자 입력을 `log.info`에 전체 출력하지 않는다. 로그 레벨과 내용을 의식적으로 선택한다.
- **외부 입력 검증**: 컨트롤러 또는 서비스 진입부에서 null/blank 여부, 길이 제한을 반드시 검증한다.

### 예외 처리

- **커스텀 예외를 사용한다**: `RuntimeException`을 직접 throw하지 않는다. 상황에 맞는 `IngestionException`, `LlmException` 등을 사용하거나 새 예외 클래스를 추가한다.
- **예외 생성 시 enum을 지정한다**: `RagExceptionEnum`에 HTTP 상태와 에러 코드를 먼저 정의하고 예외를 생성한다.
- **원인 예외(cause)를 포함한다**: 외부 시스템 오류를 래핑할 때 `new XxxException(enum, message, e)` 형태로 cause를 보존한다.
- **컨트롤러에서 `ResponseEntity.badRequest().build()`를 직접 반환하지 않는다**: 예외를 throw하고 `RagExceptionAdvice`에서 처리한다.
- **checked exception을 남용하지 않는다**: 복구 불가능한 오류는 unchecked exception으로 처리한다.

### 객체 지향 설계

- **단일 책임 원칙(SRP)**: 하나의 클래스/메서드가 하나의 책임만 가진다. 메서드가 20줄을 넘으면 분리를 검토한다.
- **개방-폐쇄 원칙(OCP)**: 새 어댑터 추가 시 기존 서비스 코드를 수정하지 않는다. 포트 인터페이스를 통해 확장한다.
- **의존성 역전(DIP)**: 서비스는 `port/` 인터페이스에만 의존한다. 구체 어댑터 클래스를 서비스에서 직접 참조하지 않는다.
- **도메인 모델은 순수하게**: `domain/`의 record에는 `@Entity`, `@Service` 등 프레임워크 어노테이션을 붙이지 않는다.
- **불변 객체를 우선**: 도메인 모델은 `record`를 사용하고, 상태 변경이 필요하면 wither 패턴(`withScore()`, `withChannel()`)을 쓴다.
- **메서드 추출로 가독성 확보**: `if/else` 중첩이 2단계를 넘으면 private 메서드로 추출한다.
- **마법 숫자 제거**: 리터럴 숫자/문자열은 상수(`static final`) 또는 설정값(`RagProperties`)으로 분리한다.

### 테스트 코드

- **단위 테스트와 통합 테스트를 분리한다**:
  - 서비스/어댑터: `@ExtendWith(MockitoExtension.class)` 단위 테스트
  - 컨트롤러: `@WebMvcTest` + `MockMvc` (Spring 웹 레이어만 로드)
  - Spring 전체 컨텍스트가 필요한 경우에만 `@SpringBootTest` 사용
- **테스트 메서드명은 행위와 기대 결과를 포함한다**: `메서드명_조건_기대결과()` 또는 `한글_서술형()` 형태로 작성한다.
- **Given-When-Then 구조를 지킨다**: 각 절을 주석 또는 빈 줄로 명확히 구분한다.
- **하나의 테스트에서 하나만 검증한다**: 복수의 `assertThat`이 필요하면 서로 다른 메서드를 검증하는지 확인한다.
- **Mock은 필요한 것만 선언한다**: `@MockitoSettings(strictness = Strictness.STRICT_STUBS)`가 기본이며, 사용되지 않는 stubbing은 제거한다. `LENIENT`로 완화가 필요하면 이유를 주석으로 남긴다.
- **예외 테스트는 구체적으로 검증한다**:
  ```java
  assertThatThrownBy(() -> service.method())
      .isInstanceOf(IngestionException.class)
      .extracting(e -> ((IngestionException) e).getExceptionEnum())
      .isEqualTo(RagExceptionEnum.EMPTY_CONTENT);
  ```
- **ChatClient 플루언트 API는 RETURNS_DEEP_STUBS로 목킹한다**:
  ```java
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ChatClient chatClient;
  // system()/user() 오버로드 충돌 방지
  when(chatClient.prompt().system(any(String.class)).user(any(String.class)).call().content())
      .thenReturn("답변");
  ```
- **테스트에서 프로덕션 코드 내부 구현에 의존하지 않는다**: private 메서드를 직접 테스트하지 않고, public API를 통해 결과를 검증한다.
- **헬퍼 메서드로 픽스처를 재사용한다**: 반복되는 객체 생성은 `private SearchHit hit(...)`, `private IngestionRequest request(...)` 형태로 분리한다.

### Spring / Java 코딩 컨벤션

- **`@RequiredArgsConstructor` + `final` 필드로 생성자 주입**: `@Autowired` 필드 주입은 사용하지 않는다.
- **서비스 빈은 무상태(stateless)로 작성한다**: 인스턴스 변수에 요청별 상태를 저장하지 않는다.
- **`Optional`을 반환값으로 남용하지 않는다**: null이 정상 케이스인 경우 `@Nullable` 또는 빈 컬렉션을 반환한다.
- **Stream을 지나치게 중첩하지 않는다**: 가독성이 떨어지면 중간 변수나 메서드 추출을 사용한다.
- **`var`는 타입이 명확할 때만 사용한다**: `var result = someService.process(...)` 처럼 타입 추론이 모호한 경우 명시적 타입을 쓴다.
- **record의 compact constructor로 검증한다**:
  ```java
  public record IngestionRequest(...) {
      public IngestionRequest { // compact constructor
          Objects.requireNonNull(docId, "docId는 필수입니다.");
      }
  }
  ```
- **네이티브 쿼리는 Repository에만 둔다**: 서비스나 어댑터에서 SQL 문자열을 직접 조합하지 않는다.
- **Jackson은 3.x(`tools.jackson.databind`)를 사용한다**: `com.fasterxml.jackson` import는 사용하지 않는다.

### 성능

- **N+1 쿼리를 방지한다**: JPA에서 연관관계 조회 시 `FETCH JOIN` 또는 `@EntityGraph`를 사용한다.
- **대량 색인은 `saveAll()`을 사용한다**: 루프 안에서 `save()`를 반복 호출하지 않는다.
- **검색 결과 크기를 제한한다**: `topNKeyword`, `topNVector`, `topKFinal`을 통해 반드시 LIMIT를 걸고 결과를 처리한다.
- **Tika 파싱은 리소스를 닫는다**: `TikaDocumentReader`는 내부적으로 InputStream을 사용하므로 예외 발생 시 리소스 누수에 주의한다.
- **가드레일 비용 인식**: `rag.guardrail.enabled=true`이면 요청당 최대 LLM 호출 2회 추가 발생. 고트래픽 환경에서는 비동기 처리 또는 캐싱 검토.
- **쿼리 전처리 비용 인식**: `LlmQueryPreprocessAdapter`가 항상 활성화되어 요청당 LLM 호출 1회 추가 발생. 전처리 실패 시 원문 쿼리를 그대로 사용(fail-open)하므로 메인 파이프라인은 차단되지 않는다.

### 일반 품질

- **디버그용 `System.out.println`은 절대 커밋하지 않는다**: 로깅은 SLF4J(`log.debug/info/warn/error`)를 사용한다.
- **로그 레벨 기준**:
  - `debug`: 내부 흐름, 건수 확인
  - `info`: 주요 파이프라인 완료 (수집 완료, 답변 생성 완료)
  - `warn`: 예상 가능한 실패 (검색 결과 없음, 가드레일 경고/차단, 파싱 경고)
  - `error`: 복구 불가능한 외부 오류 (DB 실패, LLM 호출 실패)
- **TODO/FIXME는 이슈 번호를 포함한다**: `// TODO(#123): 한국어 토크나이저로 교체 필요`
- **Lombok `@Data`는 엔티티에 사용하지 않는다**: `@Getter` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor`를 개별 지정한다.

---

## 개발 환경 설정

### 로컬 실행

```bash
# PostgreSQL + pg_search(ParadeDB) 컨테이너 시작
docker compose up -d

# 환경변수 설정
export OPENAI_API_KEY=sk-...                                      # Chat LLM + 가드레일용 (필수)
export DOUZONE_EMBEDDING_URL=https://private-ai.example.com/...   # Douzone 임베딩 API (필수, 기본값 없음)
export DB_PASSWORD=ragpass                                        # DB 패스워드 (미설정 시 ragpass 사용)

# 애플리케이션 실행 (in-memory 모드: DB 없이 테스트 가능)
./gradlew bootRun --args='--rag.keyword-search-type=memory'

# 가드레일 비활성화 (로컬 개발 시 LLM 비용 절감)
./gradlew bootRun --args='--rag.keyword-search-type=memory --rag.guardrail.enabled=false'
```

### 환경별 설정

| 프로퍼티 | 로컬/테스트 | 운영(Douzone 사내망) |
|---|---|---|
| `rag.keyword-search-type` | `memory` | `postgres` |
| `rag.embedding.type` | `douzone` | `douzone` |
| `rag.guardrail.enabled` | `false` (권장) | `true` |
| `rag.chunk.strategy` | `semantic` (기본값) | `semantic` |
| `spring.ai.openai.embedding.enabled` | `false` | `false` |
| `spring.sql.init.mode` | `always` | `never` |
| `OPENAI_API_KEY` | 발급받은 키 | 발급받은 키 또는 사내 관리 키 |
| `DOUZONE_EMBEDDING_URL` | 사내망 URL **(필수, 기본값 없음)** | 사내망 URL |
| `DB_PASSWORD` | 미설정 시 `ragpass` | 운영 DB 패스워드 |

> **임베딩 모델 전환 시 주의**: `rag.embedding.type`을 변경하면 벡터 공간이 달라지므로 기존 문서를 전부 재수집(re-ingest)해야 한다. `vector_store` 테이블은 DROP 후 재생성 필요.

### 테스트 실행

```bash
./gradlew test                    # 전체 테스트
./gradlew test --tests "*.RagExceptionAdviceTest"       # 특정 클래스
./gradlew test --tests "*.OpenAiGuardrailAdapterTest"   # 가드레일 어댑터 테스트
./gradlew test --tests "*.RagAnswerServiceTest"         # RAG 답변 서비스 테스트
```

---

## 주요 개발 패턴

### 새 어댑터 추가 방법

1. `port/` 에 인터페이스가 없으면 추가
2. `adapter/` 에 구현체 작성 + `@ConditionalOnProperty` 조건 설정
3. `application.yaml`에 조건 프로퍼티 문서화
4. 단위 테스트 작성 (`@ExtendWith(MockitoExtension.class)`)

### 새 예외 추가 방법

1. `RagExceptionEnum`에 에러 코드 + HTTP 상태 + 메시지 추가
2. 필요 시 서비스별 예외 클래스 추가 (`XxxException extends RagException`)
3. `RagExceptionAdvice`에 `@ExceptionHandler` 추가
4. `RagExceptionAdviceTest`에 단위 테스트 추가

### RAG 답변 파이프라인 흐름

```
RagController
  └─ RagAnswerService.answer()
       ├─ 1) InputGuardrailPort.check(query)         ← BLOCK이면 즉시 반환
       ├─ 2) QueryPreprocessPort.preprocess(query)   ← keywordQuery + vectorQuery 생성
       │    └─ LlmQueryPreprocessAdapter (fail-open: 실패 시 원문 그대로)
       ├─ 3) HybridSearchService.search()
       │    ├─ KeywordSearchPort  (keywordQuery로 BM25 검색)
       │    ├─ VectorSearchPort   (vectorQuery로 Cosine 검색, HyDE)
       │    ├─ RrfRankFusion      (Reciprocal Rank Fusion, 원문 query 유지)
       │    └─ RerankPort         (score 순 또는 날짜 순, 원문 query 사용)
       ├─ 4) ContextBuilder.build() (dedup / trim / sanitize)
       ├─ 5) ChatClient.prompt()   (OpenAI gpt-4o-mini 호출)
       └─ 6) OutputGuardrailPort.check(answer, context) ← BLOCK/WARN 처리
```

### 문서 수집 파이프라인 흐름

```
IngestionController
  └─ IngestionService.ingest() / ingestResource()
       ├─ TikaDocumentReader   (파일 파싱)
       ├─ ChunkSplitterPort    (청킹 전략 위임)
       │    ├─ FixedTokenChunkSplitter  [strategy=fixed, 기본값]
       │    │    └─ TokenTextSplitter (size=600)
       │    └─ SemanticChunkSplitter   [strategy=semantic, 기본값]
       │         ├─ OpenAI ChatClient → 구조 판단 + JSON chunks 반환
       │         └─ (LLM이 구조 없다고 판단·LLM 실패·과대 텍스트) → TokenTextSplitter 대체
       ├─ KeywordIndexPort     (BM25 색인 등록)
       └─ VectorStore.add()    (임베딩 + PGVector 저장)
```

---

## 커밋 메시지 컨벤션

```
<type>: <subject>

[optional body]
```

| type | 사용 시점 |
|---|---|
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 코드 구조 개선 |
| `test` | 테스트 코드 추가/수정 |
| `chore` | 빌드, 설정, 의존성 변경 |
| `docs` | 문서 변경 |

**예시**:
```
feat: OpenAI 기반 소프트 가드레일 추가

입력 가드레일(프롬프트 인젝션 차단)과 출력 가드레일(환각 감지)을
RAG 파이프라인에 통합. rag.guardrail.enabled=false이면 NoOp으로 비활성화.
```

---

## 코드 리뷰 명령어

```
/review
```

위 명령어를 실행하면 Claude Code가 이 파일의 기준에 따라 변경된 코드를 리뷰합니다.

리뷰 시 다음 항목을 중점적으로 점검합니다:
- 헥사고날 아키텍처 원칙 준수 여부
- 예외 처리 구조 일관성
- 객체 지향 설계 원칙 (SRP, OCP, DIP)
- 테스트 코드 완결성 및 품질
- 보안 취약점 (하드코딩, 인젝션, 민감 정보 노출)
- Spring/Java 컨벤션 준수
- 가드레일 Fail-open 정책 유지 여부
