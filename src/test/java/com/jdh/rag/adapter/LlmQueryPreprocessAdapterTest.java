package com.jdh.rag.adapter;

import com.jdh.rag.domain.ProcessedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmQueryPreprocessAdapterTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private LlmQueryPreprocessAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LlmQueryPreprocessAdapter(chatClient, new ObjectMapper());
    }

    // ── preprocess ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("LLM이 정상 응답하면 keywordQuery와 vectorQuery를 반환한다")
    void LLM_정상_응답시_ProcessedQuery를_반환한다() {
        String llmResponse = """
                {"keywordQuery": "연차 신청 방법", "vectorQuery": "연차 신청은 사내 포털에서 상신하며 팀장 승인 후 처리된다."}
                """;
        when(chatClient.prompt().system(any(String.class)).user(any(String.class)).call().content())
                .thenReturn(llmResponse);

        ProcessedQuery result = adapter.preprocess("연차 어떻게 신청해요?");

        assertThat(result.keywordQuery()).isEqualTo("연차 신청 방법");
        assertThat(result.vectorQuery()).contains("연차 신청");
    }

    @Test
    @DisplayName("LLM 호출 실패 시 원문 쿼리를 그대로 반환한다 (fail-open)")
    void LLM_호출_실패시_원문_쿼리를_반환한다() {
        when(chatClient.prompt().system(any(String.class)).user(any(String.class)).call().content())
                .thenThrow(new RuntimeException("OpenAI timeout"));

        ProcessedQuery result = adapter.preprocess("연차 신청 방법");

        assertThat(result.keywordQuery()).isEqualTo("연차 신청 방법");
        assertThat(result.vectorQuery()).isEqualTo("연차 신청 방법");
    }

    // ── parseResponse ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 JSON을 파싱하면 keywordQuery와 vectorQuery를 추출한다")
    void 정상_JSON_파싱시_ProcessedQuery를_반환한다() {
        String json = """
                {"keywordQuery": "세금 계산 부가세", "vectorQuery": "부가가치세는 과세표준에 10%를 곱해 산출한다."}
                """;

        ProcessedQuery result = adapter.parseResponse(json, "세금 어떻게 계산해요?");

        assertThat(result.keywordQuery()).isEqualTo("세금 계산 부가세");
        assertThat(result.vectorQuery()).isEqualTo("부가가치세는 과세표준에 10%를 곱해 산출한다.");
    }

    @Test
    @DisplayName("마크다운 코드블록으로 감싸인 JSON도 파싱한다")
    void 마크다운_코드블록_JSON도_파싱한다() {
        String response = """
                ```json
                {"keywordQuery": "연차 신청", "vectorQuery": "연차는 사내 포털에서 신청한다."}
                ```
                """;

        ProcessedQuery result = adapter.parseResponse(response, "원문");

        assertThat(result.keywordQuery()).isEqualTo("연차 신청");
        assertThat(result.vectorQuery()).isEqualTo("연차는 사내 포털에서 신청한다.");
    }

    @Test
    @DisplayName("keywordQuery가 비어있으면 원문 쿼리로 fallback한다")
    void keywordQuery_비어있으면_원문으로_fallback한다() {
        String json = """
                {"keywordQuery": "", "vectorQuery": "가상 답변"}
                """;

        ProcessedQuery result = adapter.parseResponse(json, "원문 질의");

        assertThat(result.keywordQuery()).isEqualTo("원문 질의");
        assertThat(result.vectorQuery()).isEqualTo("원문 질의");
    }

    @Test
    @DisplayName("vectorQuery가 비어있으면 원문 쿼리로 fallback한다")
    void vectorQuery_비어있으면_원문으로_fallback한다() {
        String json = """
                {"keywordQuery": "핵심어", "vectorQuery": ""}
                """;

        ProcessedQuery result = adapter.parseResponse(json, "원문 질의");

        assertThat(result.keywordQuery()).isEqualTo("원문 질의");
        assertThat(result.vectorQuery()).isEqualTo("원문 질의");
    }

    @Test
    @DisplayName("JSON 파싱 실패 시 원문 쿼리로 fallback한다")
    void JSON_파싱_실패시_원문으로_fallback한다() {
        ProcessedQuery result = adapter.parseResponse("not valid json", "원문 질의");

        assertThat(result.keywordQuery()).isEqualTo("원문 질의");
        assertThat(result.vectorQuery()).isEqualTo("원문 질의");
    }

    @Test
    @DisplayName("null 응답 시 원문 쿼리로 fallback한다")
    void null_응답시_원문으로_fallback한다() {
        ProcessedQuery result = adapter.parseResponse(null, "원문 질의");

        assertThat(result.keywordQuery()).isEqualTo("원문 질의");
        assertThat(result.vectorQuery()).isEqualTo("원문 질의");
    }
}