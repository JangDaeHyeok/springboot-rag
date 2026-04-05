package com.jdh.rag.adapter;

import com.jdh.rag.domain.SearchHit;
import com.jdh.rag.port.KeywordIndexPort;
import com.jdh.rag.port.KeywordSearchPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 개발/테스트용 인메모리 키워드 검색 어댑터.
 * 단순 term frequency 기반 스코어링 (정규화된 TF).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rag.keyword-search-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryKeywordSearchAdapter implements KeywordSearchPort, KeywordIndexPort {

    /** chunkId → SearchHit */
    private final Map<String, SearchHit> store = new LinkedHashMap<>();

    @Override
    public void index(List<SearchHit> hits) {
        if (hits == null) return;
        hits.forEach(hit -> store.put(hit.id(), hit));
        log.info("인메모리 키워드 인덱스 크기: {}", store.size());
    }

    @Override
    public List<SearchHit> search(String query, int topN, Map<String, Object> filters) {
        if (query == null || query.isBlank()) return List.of();
        String[] terms = query.toLowerCase().split("\\s+");

        return store.values().stream()
                .filter(h -> matchesFilters(h, filters))
                .map(h -> Map.entry(h, score(h, terms)))
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<SearchHit, Double>comparingByValue().reversed())
                .limit(topN)
                .map(e -> e.getKey().withScore(e.getValue()).withChannel("lexical"))
                .toList();
    }

    /** 테스트 격리용 */
    public void clear() { store.clear(); }

    public int size() { return store.size(); }

    private double score(SearchHit hit, String[] terms) {
        String text = hit.content() == null ? "" : hit.content().toLowerCase();
        int textLen = Math.max(text.length(), 1);
        double score = 0;
        for (String term : terms) {
            // 정규화된 TF: 긴 문서에 불이익
            score += (double) countOccurrences(text, term) / (textLen / 200.0 + 1);
        }
        return score;
    }

    private int countOccurrences(String text, String term) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(term, idx)) != -1) {
            count++;
            idx += term.length();
        }
        return count;
    }

    private boolean matchesFilters(SearchHit hit, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return true;
        Map<String, Object> meta = hit.meta();
        if (meta == null) return false;
        return filters.entrySet().stream()
                .allMatch(e -> {
                    Object v = meta.get(e.getKey());
                    return v != null && v.toString().equals(e.getValue().toString());
                });
    }
}