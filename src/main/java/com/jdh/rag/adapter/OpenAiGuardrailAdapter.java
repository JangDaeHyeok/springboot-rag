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
 * LLM 기반 소프트 가드레일 어댑터. rag.guardrail.enabled=true 시 활성화.
 *
 * 입력: 프롬프트 인젝션·유해 요청 감지 (PASS/WARN/BLOCK).
 * 출력: 답변이 문서에 근거하는지 검증 (환각 감지).
 * Fail-open: LLM 오류 시 PASS 처리. 요청당 최대 LLM 2회 추가.
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

    /** 입력 가드레일: 프롬프트 인젝션·유해 요청 → BLOCK, 범위 외 → WARN, 정상 → PASS */
    @Override
    public GuardrailResult check(String query) {
        return callAndParse(
                prompts.inputUserTemplate().formatted(query),
                prompts.inputWarnedMessage(),
                prompts.inputBlockedMessage()
        );
    }

    // ── OutputGuardrailPort ───────────────────────────────────────────────────

    /** 출력 가드레일: 문서 없는 내용 단언(환각) → BLOCK, 근거 부족 → WARN, 근거 충분 → PASS */
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

    /** LLM 응답 JSON → GuardrailResult 변환. 파싱 실패 시 fail-open(PASS). */
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
