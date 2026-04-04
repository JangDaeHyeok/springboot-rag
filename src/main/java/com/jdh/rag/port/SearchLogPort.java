package com.jdh.rag.port;

import com.jdh.rag.domain.SearchLog;

import java.util.List;

/**
 * 검색 로그 적재 포트.
 * SLF4J 출력 외에 DB 영속화를 담당한다.
 */
public interface SearchLogPort {

    void save(SearchLog log);

    void saveBatch(List<SearchLog> logs);
}
