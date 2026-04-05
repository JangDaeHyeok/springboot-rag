package com.jdh.rag.adapter;

import com.jdh.rag.domain.SearchHit;
import com.jdh.rag.port.KeywordIndexPort;
import com.jdh.rag.port.KeywordSearchPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * pg_search(ParadeDB) BM25 기반 키워드 검색·색인 어댑터.
 * paradedb.match() + paradedb.score() 를 네이티브 쿼리로 사용한다.
 * BM25 인덱스(idx_rag_chunks_bm25)는 DDL에서 생성되며 색인 시 별도 처리 불필요.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.keyword-search-type", havingValue = "postgres")
public class PgKeywordSearchAdapter implements KeywordSearchPort, KeywordIndexPort {

    private final RagChunkJpaRepository ragChunkJpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<SearchHit> search(String query, int topN, Map<String, Object> filters) {
        if (query == null || query.isBlank()) return List.of();

        String tenantId = filterStr(filters, "tenantId");
        String domain   = filterStr(filters, "domain");

        List<Object[]> rows = ragChunkJpaRepository.findByPgSearch(query, tenantId, domain, topN);

        List<SearchHit> hits = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String chunkId           = str(row[0]);
            String docId             = str(row[1]);
            String content           = str(row[2]);
            Map<String, Object> meta = parseMeta(str(row[3]));
            double score             = row[4] instanceof Number n ? n.doubleValue() : 0.0;

            hits.add(new SearchHit(chunkId, docId, content, meta, score, "lexical"));
        }
        return hits;
    }

    @Override
    public void index(List<SearchHit> hits) {
        if (hits == null || hits.isEmpty()) return;

        List<RagChunkEntity> entities = hits.stream()
                .map(this::toEntity)
                .toList();

        ragChunkJpaRepository.saveAll(entities);
        log.info("pg_search BM25 색인 완료: {}건", entities.size());
    }

    private RagChunkEntity toEntity(SearchHit hit) {
        Map<String, Object> meta = hit.meta() != null ? hit.meta() : Map.of();
        return RagChunkEntity.builder()
                .chunkId(hit.id())
                .docId(hit.docId())
                .content(hit.content())
                .tenantId(metaStr(meta, "tenantId"))
                .domain(metaStr(meta, "domain"))
                .version(metaStr(meta, "version"))
                .source(metaStr(meta, "source"))
                .createdAt(Instant.now())
                .metadata(meta)
                .build();
    }

    private static final TypeReference<Map<String, Object>> META_TYPE = new TypeReference<>() {};

    private Map<String, Object> parseMeta(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, META_TYPE);
        } catch (Exception e) {
            log.warn("metadata JSON 파싱 실패: {}", e.getMessage());
            return Map.of();
        }
    }

    private String filterStr(Map<String, Object> filters, String key) {
        if (filters == null) return null;
        Object v = filters.get(key);
        return v != null ? v.toString() : null;
    }

    private String metaStr(Map<String, Object> meta, String key) {
        Object v = meta.get(key);
        return v != null ? v.toString() : null;
    }

    private String str(Object o) {
        return o != null ? o.toString() : "";
    }
}