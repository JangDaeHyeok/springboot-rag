package com.jdh.rag.adapter;

import com.jdh.rag.domain.ProcessedQuery;
import com.jdh.rag.port.QueryPreprocessPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 쿼리 전처리 비활성화 어댑터.
 *
 * <p>{@code rag.query-preprocess.enabled=false} 일 때 활성화된다.
 * 원문 쿼리를 keywordQuery/vectorQuery 양쪽 모두에 그대로 사용한다.
 *
 * <p>로컬 개발·테스트 환경에서 LLM 호출 비용 없이 파이프라인을 검증할 때 사용한다.
 */
@Component
@ConditionalOnProperty(name = "rag.query-preprocess.enabled", havingValue = "false")
public class NoOpQueryPreprocessAdapter implements QueryPreprocessPort {

    @Override
    public ProcessedQuery preprocess(String query) {
        return new ProcessedQuery(query, query);
    }
}