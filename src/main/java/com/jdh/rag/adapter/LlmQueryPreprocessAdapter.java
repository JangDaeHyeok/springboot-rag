package com.jdh.rag.adapter;

import com.jdh.rag.domain.ProcessedQuery;
import com.jdh.rag.port.QueryPreprocessPort;
import com.jdh.rag.support.prompt.QueryPreprocessPrompts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI ChatClient 기반 쿼리 전처리 어댑터.
 *
 * <p>원문 쿼리를 두 가지 형태로 변환한다.
 * <ul>
 *   <li><b>keywordQuery</b>: 구어체·조사를 제거하고 핵심 명사·동사 중심으로 정제. BM25 recall 향상.</li>
 *   <li><b>vectorQuery</b>: HyDE(Hypothetical Document Embeddings) — 질문의 이상적인 답변이
 *       담긴 가상 문서 2~3문장. 의미 공간에서 실제 문서 청크에 더 가깝게 근접.</li>
 * </ul>
 *
 * <p><b>Fail-open 정책</b>: LLM 호출 실패 또는 응답 파싱 실패 시 원문 쿼리를 그대로 사용한다.
 * 전처리가 메인 파이프라인을 차단하지 않도록 설계되었다.
 *
 * <p><b>비용 주의</b>: 요청당 LLM 호출 1회 추가 발생.
 */
@Slf4j
@Component
@RequiredArgsConstructor
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
            log.warn("쿼리 전처리 LLM 호출 실패 (원문 사용): query={}, error={}", query, e.getMessage());
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

            log.debug("쿼리 전처리 완료: keywordQuery={}, vectorQuery(len={})",
                    keywordQuery, vectorQuery.length());
            return new ProcessedQuery(keywordQuery, vectorQuery);

        } catch (Exception e) {
            log.warn("쿼리 전처리 응답 파싱 실패 (원문 사용): response={}, error={}",
                    responseText, e.getMessage());
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
