package com.jdh.rag.port;

import com.jdh.rag.domain.SearchHit;

import java.util.List;
import java.util.Map;

/** 벡터 유사도 검색 포트. 구현체: SpringAiVectorSearchAdapter */
public interface VectorSearchPort {

    /** @param threshold cosine 유사도 하한값 (null이면 필터 없음) */
    List<SearchHit> search(String query, int topN, Double threshold, Map<String, Object> filters);
}
