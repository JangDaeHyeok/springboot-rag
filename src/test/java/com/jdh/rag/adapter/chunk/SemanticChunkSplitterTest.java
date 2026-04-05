package com.jdh.rag.adapter.chunk;

import com.jdh.rag.config.RagProperties;
import com.jdh.rag.support.prompt.ChunkSplitterPrompts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
// LENIENT: @BeforeEach의 ragProperties.chunk().size() stubbing이
//          parseChunks 단위 테스트 등 일부 케이스에서 사용되지 않는다.
class SemanticChunkSplitterTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private RagProperties ragProperties;

    private SemanticChunkSplitter splitter;

    @BeforeEach
    void setUp() {
        RagProperties.ChunkProperties chunk = mock(RagProperties.ChunkProperties.class);
        when(ragProperties.chunk()).thenReturn(chunk);
        when(chunk.size()).thenReturn(600);

        splitter = new SemanticChunkSplitter(chatClient, ragProperties, new ObjectMapper(), new ChunkSplitterPrompts());
    }

    // ── 시맨틱 청킹 (LLM이 구조 있다고 판단) ─────────────────────────────────

    @Test
    @DisplayName("LLM이 조항 구조를 감지하면 청크 목록을 반환한다")
    void LLM이_조항_구조_감지_시_청크_반환() {
        String text = """
                제1조 (목적)
                본 규정은 임직원의 복무 기준을 정함을 목적으로 한다.

                제2조 (적용 범위)
                이 규정은 모든 임직원에게 적용된다.
                """;
        String llmResponse = """
                {"chunks": ["제1조 (목적)\\n본 규정은 임직원의 복무 기준을 정함을 목적으로 한다.", "제2조 (적용 범위)\\n이 규정은 모든 임직원에게 적용된다."]}
                """;

        when(chatClient.prompt().system(any(String.class)).user(any(String.class)).call().content())
                .thenReturn(llmResponse);

        List<Document> result = splitter.split(List.of(new Document(text)));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getText()).contains("제1조");
        assertThat(result.get(1).getText()).contains("제2조");
    }

    @Test
    @DisplayName("LLM이 번호 목록 구조를 감지하면 청크 목록을 반환한다")
    void LLM이_번호_목록_구조_감지_시_청크_반환() {
        String llmResponse = """
                {"chunks": ["1. 첫 번째 항목입니다.", "2. 두 번째 항목입니다."]}
                """;

        when(chatClient.prompt().system(any(String.class)).user(any(String.class)).call().content())
                .thenReturn(llmResponse);

        List<Document> result = splitter.split(List.of(new Document("1. 첫 번째 항목입니다.\n\n2. 두 번째 항목입니다.")));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("메타데이터가 각 청크에 복사된다")
    void 시맨틱_청킹_시_메타데이터_복사() {
        String llmResponse = "{\"chunks\": [\"제1조\\n내용.\", \"제2조\\n내용.\"]}";

        when(chatClient.prompt().system(any(String.class)).user(any(String.class)).call().content())
                .thenReturn(llmResponse);

        Document doc = new Document("제1조\n내용.\n\n제2조\n내용.");
        doc.getMetadata().put("docId", "test-doc");

        List<Document> result = splitter.split(List.of(doc));

        assertThat(result).allMatch(d -> "test-doc".equals(d.getMetadata().get("docId")));
    }

    // ── 고정 크기 청킹으로 대체 ───────────────────────────────────────────────

    @Test
    @DisplayName("LLM이 구조 없다고 판단하면(빈 배열 반환) 고정 크기 청킹으로 대체한다")
    void LLM이_구조_없다고_판단_시_고정_크기_청킹_대체() {
        String text = "이것은 일반적인 문단입니다. 조항이나 번호 구조가 없습니다. ".repeat(5);

        when(chatClient.prompt().system(any(String.class)).user(any(String.class)).call().content())
                .thenReturn("{\"chunks\": []}");

        List<Document> result = splitter.split(List.of(new Document(text)));

        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("MAX_SEMANTIC_CHARS를 초과하는 문서는 LLM 호출 없이 고정 크기 청킹으로 대체한다")
    void 과대_텍스트_LLM_호출_없이_고정_크기_청킹_대체() {
        String text = "내용 ".repeat(10_001); // > 20_000 chars
        assertThat(text.length()).isGreaterThan(SemanticChunkSplitter.MAX_SEMANTIC_CHARS);

        List<Document> result = splitter.split(List.of(new Document(text)));

        assertThat(result).isNotEmpty();
        verifyNoInteractions(chatClient);
    }

    @Test
    @DisplayName("LLM 호출 실패 시 고정 크기 청킹으로 대체한다")
    void LLM_호출_실패_시_고정_크기_청킹_대체() {
        when(chatClient.prompt().system(any(String.class)).user(any(String.class)).call().content())
                .thenThrow(new RuntimeException("OpenAI 호출 실패"));

        List<Document> result = splitter.split(List.of(new Document("제1조 (목적)\n본 규정은 임직원의 복무 기준을 정함을 목적으로 한다.")));

        assertThat(result).isNotEmpty();
    }

    // ── parseChunks 단위 테스트 ────────────────────────────────────────────────

    @Test
    @DisplayName("정상 JSON 응답을 청크 목록으로 파싱한다")
    void parseChunks_정상_JSON_파싱() {
        List<String> chunks = splitter.parseChunks("{\"chunks\": [\"청크1\", \"청크2\", \"청크3\"]}");

        assertThat(chunks).containsExactly("청크1", "청크2", "청크3");
    }

    @Test
    @DisplayName("마크다운 코드블록으로 감싸인 JSON도 파싱한다")
    void parseChunks_마크다운_코드블록_파싱() {
        List<String> chunks = splitter.parseChunks("```json\n{\"chunks\": [\"청크1\", \"청크2\"]}\n```");

        assertThat(chunks).containsExactly("청크1", "청크2");
    }

    @Test
    @DisplayName("JSON 앞뒤 설명 텍스트가 있어도 파싱한다")
    void parseChunks_앞뒤_설명_텍스트_파싱() {
        List<String> chunks = splitter.parseChunks("분석 결과: {\"chunks\": [\"청크1\"]} 이상 없음.");

        assertThat(chunks).containsExactly("청크1");
    }

    @Test
    @DisplayName("빈 chunks 배열이면 빈 컬렉션을 반환한다")
    void parseChunks_빈_배열_빈_컬렉션_반환() {
        assertThat(splitter.parseChunks("{\"chunks\": []}")).isEmpty();
    }

    @Test
    @DisplayName("잘못된 JSON이면 빈 컬렉션을 반환한다")
    void parseChunks_잘못된_JSON_빈_컬렉션_반환() {
        assertThat(splitter.parseChunks("not json")).isEmpty();
    }

    @Test
    @DisplayName("null 응답이면 빈 컬렉션을 반환한다")
    void parseChunks_null_응답_빈_컬렉션_반환() {
        assertThat(splitter.parseChunks(null)).isEmpty();
    }
}
