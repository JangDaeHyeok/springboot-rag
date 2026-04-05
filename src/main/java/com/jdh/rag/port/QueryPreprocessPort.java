package com.jdh.rag.port;

import com.jdh.rag.domain.ProcessedQuery;

/**
 * 쿼리 전처리 포트.
 * 사용자 원문 쿼리를 검색 채널(BM25·Vector)에 맞게 각각 변환한다.
 *
 * <p>구현체: {@link com.jdh.rag.adapter.LlmQueryPreprocessAdapter}
 */
public interface QueryPreprocessPort {

    /**
     * 원문 쿼리를 전처리한다.
     *
     * @param query 사용자 원문 질의
     * @return BM25용 keywordQuery + Vector용 vectorQuery
     */
    ProcessedQuery preprocess(String query);
}
