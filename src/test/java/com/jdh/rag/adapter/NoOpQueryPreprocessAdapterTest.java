package com.jdh.rag.adapter;

import com.jdh.rag.domain.ProcessedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpQueryPreprocessAdapterTest {

    private final NoOpQueryPreprocessAdapter adapter = new NoOpQueryPreprocessAdapter();

    @Test
    @DisplayName("원문 쿼리를 keywordQuery와 vectorQuery 양쪽 모두에 그대로 반환한다")
    void 원문_쿼리를_그대로_반환한다() {
        ProcessedQuery result = adapter.preprocess("연차 신청 방법");

        assertThat(result.keywordQuery()).isEqualTo("연차 신청 방법");
        assertThat(result.vectorQuery()).isEqualTo("연차 신청 방법");
    }

    @Test
    @DisplayName("빈 쿼리도 그대로 반환한다")
    void 빈_쿼리도_그대로_반환한다() {
        ProcessedQuery result = adapter.preprocess("");

        assertThat(result.keywordQuery()).isEmpty();
        assertThat(result.vectorQuery()).isEmpty();
    }
}