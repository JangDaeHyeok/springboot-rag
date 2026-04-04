package com.jdh.rag.support;

import com.jdh.rag.domain.SearchHit;
import com.jdh.rag.domain.SearchLog;
import com.jdh.rag.port.SearchLogPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchLoggerTest {

    @Mock private SearchLogPort searchLogPort;
    @Captor private ArgumentCaptor<List<SearchLog>> captor;

    private SearchLogger searchLogger;

    @BeforeEach
    void setUp() {
        searchLogger = new SearchLogger(new ObjectMapper(), searchLogPort);
    }

    @Test
    @DisplayName("logBatch 후보 수만큼 saveBatch를 호출한다")
    void logBatch_후보수_만큼_saveBatch를_호출한다() {
        List<SearchHit> candidates = List.of(hit("A"), hit("B"), hit("C"));
        List<SearchHit> used = List.of(hit("A"), hit("B"));

        searchLogger.logBatch("req-1", "질의", candidates, used);

        verify(searchLogPort).saveBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
    }

    @Test
    @DisplayName("usedInPrompt 플래그가 올바르게 설정된다")
    void usedInPrompt_플래그가_올바르게_설정된다() {
        List<SearchHit> candidates = List.of(hit("A"), hit("B"), hit("C"));
        List<SearchHit> used = List.of(hit("B"));

        searchLogger.logBatch("req-2", "질의", candidates, used);

        verify(searchLogPort).saveBatch(captor.capture());

        List<SearchLog> logs = captor.getValue();
        assertThat(logs.get(0).usedInPrompt()).isFalse();  // A
        assertThat(logs.get(1).usedInPrompt()).isTrue();   // B
        assertThat(logs.get(2).usedInPrompt()).isFalse();  // C
    }

    @Test
    @DisplayName("rank는 1부터 시작한다")
    void rank는_1부터_시작한다() {
        List<SearchHit> candidates = List.of(hit("A"), hit("B"));

        searchLogger.logBatch("req-3", "질의", candidates, List.of());

        verify(searchLogPort).saveBatch(captor.capture());

        assertThat(captor.getValue().get(0).rank()).isEqualTo(1);
        assertThat(captor.getValue().get(1).rank()).isEqualTo(2);
    }

    @Test
    @DisplayName("saveBatch 실패 시 예외를 전파하지 않는다")
    void saveBatch_실패시_예외를_전파하지_않는다() {
        doThrow(new RuntimeException("DB 연결 실패")).when(searchLogPort).saveBatch(any());

        List<SearchHit> candidates = List.of(hit("A"));
        // 예외가 전파되지 않아야 한다
        searchLogger.logBatch("req-4", "질의", candidates, List.of());
    }

    @Test
    @DisplayName("빈 candidates이면 saveBatch에 빈 목록을 전달한다")
    void 빈_candidates이면_saveBatch에_빈_목록을_전달한다() {
        searchLogger.logBatch("req-5", "질의", List.of(), List.of());

        verify(searchLogPort).saveBatch(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    private SearchHit hit(String id) {
        return new SearchHit(id, "doc-" + id, "content " + id, Map.of(), 0.5, "fused");
    }
}
