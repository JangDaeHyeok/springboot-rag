package com.jdh.rag.adapter;

import com.jdh.rag.domain.ProcessedQuery;
import com.jdh.rag.port.QueryPreprocessPort;
import com.jdh.rag.support.prompt.QueryPreprocessPrompts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * LLM 기반 쿼리 전처리 어댑터. rag.query-preprocess.enabled=true(기본값) 시 활성화.
 *
 * 원문 쿼리 → keywordQuery(BM25용 핵심어) + vectorQuery(HyDE 가상 답변).
 * Fail-open: 실패 시 원문 그대로 반환. 요청당 LLM 1회 추가.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.query-preprocess.enabled", havingValue = "true", matchIfMissing = true)
public class LlmQueryPreprocessAdapter implements QueryPreprocessPort {

    private final ChatClient            chatClient;
    private final ObjectMapper          objectMapper;
    private final QueryPreprocessPrompts prompts;

    @Override
    public ProcessedQuery preprocess(String query) {
        try {
            String response = chatClient.prompt()
                    .system(prompts.system())
                    .user(prompts.userTemplate().formatted(query))
                    .call()
                    .content();
            return parseResponse(response, query);
        } catch (Exception e) {
            log.warn("쿼리 전처리 LLM 호출 실패 (원문 사용): error={}", e.getMessage());
            return fallback(query);
        }
    }

    ProcessedQuery parseResponse(String responseText, String originalQuery) {
        try {
            String json = extractJson(responseText);
            JsonNode node = objectMapper.readTree(json);

            String keywordQuery = node.path("keywordQuery").asText("").strip();
            String vectorQuery  = node.path("vectorQuery").asText("").strip();

            if (keywordQuery.isBlank() || vectorQuery.isBlank()) {
                log.warn("쿼리 전처리 응답 불완전 (원문 사용): response={}", responseText);
                return fallback(originalQuery);
            }

            log.info("쿼리 전처리 완료: keywordQuery={}, vectorQuery(len={})",
                    keywordQuery, vectorQuery.length());
            return new ProcessedQuery(keywordQuery, vectorQuery);

        } catch (Exception e) {
            log.warn("쿼리 전처리 응답 파싱 실패 (원문 사용): responseLen={}, error={}",
                    responseText != null ? responseText.length() : 0, e.getMessage());
            return fallback(originalQuery);
        }
    }

    private ProcessedQuery fallback(String query) {
        return new ProcessedQuery(query, query);
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
