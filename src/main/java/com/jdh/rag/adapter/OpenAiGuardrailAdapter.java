package com.jdh.rag.adapter;

import com.jdh.rag.domain.GuardrailResult;
import com.jdh.rag.port.InputGuardrailPort;
import com.jdh.rag.port.OutputGuardrailPort;
import com.jdh.rag.support.prompt.GuardrailPrompts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI ChatClient 기반 소프트 가드레일 어댑터.
 *
 * <p>{@code rag.guardrail.enabled=true} 일 때 활성화된다.
 *
 * <p><b>입력 가드레일 ({@link #check(String)})</b>:
 * 사용자 질의에서 프롬프트 인젝션·악의적 요청·범위 외 질문을 감지한다.
 *
 * <p><b>출력 가드레일 ({@link #check(String, String)})</b>:
 * LLM 답변이 참고 문서에 충분히 근거하는지 확인한다.
 * 문서에 없는 내용을 사실처럼 단언하거나(hallucination) 내용을 왜곡하면 WARN/BLOCK을 반환한다.
 *
 * <p><b>Fail-open 정책</b>: 가드레일 판단 중 오류가 발생하면 PASS로 처리한다.
 * 가드레일이 메인 파이프라인을 차단하지 않도록 설계되었다.
 *
 * <p><b>비용 주의</b>: 요청당 최대 2번의 추가 LLM 호출이 발생한다.
 * 고트래픽 환경에서는 캐싱 또는 비동기 처리를 검토한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.guardrail.enabled", havingValue = "true")
public class OpenAiGuardrailAdapter implements InputGuardrailPort, OutputGuardrailPort {

    private final ChatClient     chatClient;
    private final ObjectMapper   objectMapper;
    private final GuardrailPrompts prompts;

    // ── InputGuardrailPort ────────────────────────────────────────────────────

    /**
     * 사용자 질의 검사.
     * <ul>
     *   <li>BLOCK: 프롬프트 인젝션, 역할 변경 시도, 개인정보 탈취, 유해 콘텐츠 요청</li>
     *   <li>WARN:  업무 범위 외 질문</li>
     *   <li>PASS:  정상적인 업무·지식 관련 질문</li>
     * </ul>
     */
    @Override
    public GuardrailResult check(String query) {
        return callAndParse(
                prompts.inputUserTemplate().formatted(query),
                prompts.inputWarnedMessage(),
                prompts.inputBlockedMessage()
        );
    }

    // ── OutputGuardrailPort ───────────────────────────────────────────────────

    /**
     * 생성된 답변의 문서 근거 검사.
     * <ul>
     *   <li>BLOCK: 문서에 없는 내용을 단언하거나 내용을 심각하게 왜곡</li>
     *   <li>WARN:  문서 근거가 불충분하거나 불확실한 내용 포함</li>
     *   <li>PASS:  답변이 문서에 충분히 근거하거나 불확실성을 적절히 표시</li>
     * </ul>
     */
    @Override
    public GuardrailResult check(String answer, String contextText) {
        return callAndParse(
                prompts.outputUserTemplate().formatted(contextText, answer),
                prompts.outputWarnedMessage(),
                prompts.outputBlockedMessage()
        );
    }

    // ── 내부 처리 ──────────────────────────────────────────────────────────────

    private GuardrailResult callAndParse(String userPrompt,
                                          String warnMessage,
                                          String blockMessage) {
        String responseText;
        try {
            responseText = chatClient.prompt()
                    .system(prompts.system())
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("가드레일 LLM 호출 실패 (fail-open): {}", e.getMessage());
            return GuardrailResult.pass();
        }

        return parseResponse(responseText, warnMessage, blockMessage);
    }

    /**
     * LLM 응답 JSON을 {@link GuardrailResult}로 변환한다.
     * 파싱 실패 시 fail-open(PASS)으로 처리한다.
     */
    GuardrailResult parseResponse(String responseText,
                                   String warnMessage,
                                   String blockMessage) {
        try {
            String json = extractJson(responseText);
            JsonNode node = objectMapper.readTree(json);

            String status = node.path("status").asText("PASS").toUpperCase();
            String reason = node.path("reason").asText("");

            return switch (status) {
                case "BLOCK" -> GuardrailResult.block(reason, blockMessage);
                case "WARN"  -> GuardrailResult.warn(reason, warnMessage);
                default      -> GuardrailResult.pass();
            };
        } catch (Exception e) {
            log.warn("가드레일 응답 파싱 실패 (fail-open): responseLen={}, error={}",
                    responseText != null ? responseText.length() : 0, e.getMessage());
            return GuardrailResult.pass();
        }
    }

    /**
     * 마크다운 코드블록(```json ... ```)으로 감싸진 경우를 처리한다.
     */
    private String extractJson(String text) {
        if (text == null) return "{}";
        text = text.strip();
        int start = text.indexOf('{');
        int end   = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
