package com.jdh.rag.port;

import com.jdh.rag.domain.SearchHit;

import java.util.List;
import java.util.Map;

/** BM25 키워드 검색 포트. 구현체: PgKeywordSearchAdapter, InMemoryKeywordSearchAdapter */
public interface KeywordSearchPort {

    List<SearchHit> search(String query, int topN, Map<String, Object> filters);
}