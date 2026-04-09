package com.jdh.rag.adapter;

import com.jdh.rag.domain.SearchHit;
import com.jdh.rag.port.VectorSearchPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Spring AI VectorStore 기반 벡터 검색 어댑터.
 * VectorStore 구현체(SimpleVectorStore/pgvector/Qdrant 등)를 교체해도 코드 변경 없음.
 *
 * 필터 전략:
 * - DB 중립성 확보를 위해 기본은 앱 후처리 필터 사용.
 * - 특정 VectorStore에서 pushdown 필터 최적화가 필요한 경우 filterExpression 옵션 활성화.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringAiVectorSearchAdapter implements VectorSearchPort {

    private final VectorStore vectorStore;

    @Override
    public List<SearchHit> search(String query, int topN, Double threshold,
                                   Map<String, Object> filters) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topN);

        if (threshold != null) {
            builder.similarityThreshold(threshold);
        }

        String filterExpression = buildFilterExpression(filters);
        if (filterExpression != null) {
            builder.filterExpression(filterExpression);
        }

        List<Document> docs = vectorStore.similaritySearch(builder.build());

        return docs.stream()
                .filter(d -> matchesFilters(d.getMetadata(), filters))
                .map(this::toSearchHit)
                .toList();
    }

    private SearchHit toSearchHit(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        String chunkId = metaStr(meta, "chunkId", doc.getId());
        String docId   = metaStr(meta, "docId",   doc.getId());

        // Spring AI는 Document에 score 필드를 직접 노출하지 않음
        // 디버깅이 필요한 경우 metadata에 "_score" 키로 저장하도록 ingestion 시 추가 가능
        Double score = meta.containsKey("_score")
                ? Double.parseDouble(meta.get("_score").toString()) : null;

        return new SearchHit(chunkId, docId, doc.getText(), meta, score, "vector");
    }

    private boolean matchesFilters(Map<String, Object> meta, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return true;
        if (meta == null) return false;
        for (var entry : filters.entrySet()) {
            Object v = meta.get(entry.getKey());
            if (v == null || !v.toString().equals(entry.getValue().toString())) return false;
        }
        return true;
    }

    private String buildFilterExpression(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return null;
        return filters.entrySet().stream()
                .map(entry -> entry.getKey() + " == '" + escapeFilterValue(entry.getValue()) + "'")
                .reduce((left, right) -> left + " && " + right)
                .orElse(null);
    }

    private String escapeFilterValue(Object value) {
        return value == null ? "" : value.toString().replace("\\", "\\\\").replace("'", "\\'");
    }

    private String metaStr(Map<String, Object> meta, String key, String fallback) {
        if (meta == null) return fallback;
        Object v = meta.get(key);
        return v != null ? v.toString() : fallback;
    }
}
