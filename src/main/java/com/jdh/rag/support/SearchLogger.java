package com.jdh.rag.support;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.jdh.rag.domain.SearchHit;
import com.jdh.rag.domain.SearchLog;
import com.jdh.rag.port.SearchLogPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 검색 로그 적재.
 * SLF4J JSON 출력(스트리밍 수집용) + DB 영속화(cosine threshold 튜닝용).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchLogger {

    private final ObjectMapper objectMapper;
    private final SearchLogPort searchLogPort;

    /**
     * @param candidates   RRF 결합 후 전체 후보 (topN)
     * @param usedInPrompt LLM에 실제로 전달한 chunks (topK)
     */
    public void logBatch(String requestId, String query,
                          List<SearchHit> candidates, List<SearchHit> usedInPrompt) {
        Set<String> usedIds = usedInPrompt.stream()
                .map(SearchHit::id)
                .collect(Collectors.toSet());

        Instant now = Instant.now();
        List<SearchLog> entries = new ArrayList<>(candidates.size());

        for (int i = 0; i < candidates.size(); i++) {
            SearchHit hit = candidates.get(i);
            SearchLog entry = SearchLog.builder()
                    .requestId(requestId).query(query)
                    .docId(hit.docId()).chunkId(hit.id())
                    .cosineScore(hit.score())       // RRF 점수 (원 cosine은 추후 개선)
                    .rank(i + 1)
                    .usedInPrompt(usedIds.contains(hit.id()))
                    // answerAccepted는 피드백 API로 업데이트
                    .channel(hit.channel())
                    .createdAt(now)
                    .build();
            entries.add(entry);
            logEntry(entry);
        }

        try {
            searchLogPort.saveBatch(entries);
        } catch (Exception e) {
            log.error("SearchLog DB 적재 실패 (requestId={}): {}", requestId, e.getMessage());
        }
    }

    private void logEntry(SearchLog searchLog) {
        try {
            log.info("[SEARCH_LOG] {}", objectMapper.writeValueAsString(searchLog));
        } catch (JacksonException e) {
            log.warn("SearchLog 직렬화 실패: {}", e.getMessage());
        }
    }
}