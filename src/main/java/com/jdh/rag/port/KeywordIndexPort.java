package com.jdh.rag.port;

import com.jdh.rag.domain.SearchHit;

import java.util.List;

/**
 * 키워드 색인 포트. Ingestion 파이프라인 전용.
 * 동일 id는 upsert(덮어쓰기) 한다.
 */
public interface KeywordIndexPort {

    void index(List<SearchHit> hits);
}
