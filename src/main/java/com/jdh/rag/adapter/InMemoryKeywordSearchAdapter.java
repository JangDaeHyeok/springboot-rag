package com.jdh.rag.adapter;

import com.jdh.rag.domain.DocumentInfo;
import com.jdh.rag.domain.SearchHit;
import com.jdh.rag.port.DocumentManagementPort;
import com.jdh.rag.port.KeywordIndexPort;
import com.jdh.rag.port.KeywordSearchPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 개발/테스트용 인메모리 키워드 검색 어댑터.
 * 단순 term frequency 기반 스코어링 (정규화된 TF).
 * DocumentManagementPort 도 함께 구현하여 메모리 모드에서 문서 관리 기능을 제공한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rag.keyword-search-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryKeywordSearchAdapter implements KeywordSearchPort, KeywordIndexPort, DocumentManagementPort {

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

    // ── DocumentManagementPort ────────────────────────────────────────────────

    @Override
    public boolean existsByDocId(String docId) {
        return store.values().stream().anyMatch(h -> docId.equals(h.docId()));
    }

    @Override
    public Optional<DocumentInfo> findByDocId(String docId) {
        List<SearchHit> hits = store.values().stream()
                .filter(h -> docId.equals(h.docId()))
                .toList();
        if (hits.isEmpty()) return Optional.empty();
        SearchHit first = hits.get(0);
        return Optional.of(new DocumentInfo(
                docId,
                metaStr(first, "source"),
                metaStr(first, "domain"),
                metaStr(first, "version"),
                metaStr(first, "tenantId"),
                hits.size(),
                null
        ));
    }

    @Override
    public List<DocumentInfo> listDocuments(String tenantId, String domain) {
        Map<String, List<SearchHit>> byDocId = new LinkedHashMap<>();
        for (SearchHit hit : store.values()) {
            if (tenantId != null && !tenantId.equals(metaStr(hit, "tenantId"))) continue;
            if (domain   != null && !domain.equals(metaStr(hit, "domain")))     continue;
            byDocId.computeIfAbsent(hit.docId(), k -> new ArrayList<>()).add(hit);
        }
        return byDocId.entrySet().stream()
                .map(e -> {
                    SearchHit first = e.getValue().get(0);
                    return new DocumentInfo(
                            e.getKey(),
                            metaStr(first, "source"),
                            metaStr(first, "domain"),
                            metaStr(first, "version"),
                            metaStr(first, "tenantId"),
                            e.getValue().size(),
                            null    // 인메모리는 createdAt 미지원
                    );
                })
                .toList();
    }

    @Override
    public void deleteByDocId(String docId) {
        int before = store.size();
        store.entrySet().removeIf(e -> docId.equals(e.getValue().docId()));
        log.info("인메모리 키워드 인덱스에서 docId={} 삭제: {}건 제거", docId, before - store.size());
    }

    // ── 테스트 유틸 ───────────────────────────────────────────────────────────

    /** 테스트 격리용 */
    public void clear() { store.clear(); }

    public int size() { return store.size(); }

    // ── private ───────────────────────────────────────────────────────────────

    private double score(SearchHit hit, String[] terms) {
        String text = hit.content() == null ? "" : hit.content().toLowerCase();
        int textLen = Math.max(text.length(), 1);
        double score = 0;
        for (String term : terms) {
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

    private String metaStr(SearchHit hit, String key) {
        if (hit.meta() == null) return null;
        Object v = hit.meta().get(key);
        return v != null ? v.toString() : null;
    }
}
