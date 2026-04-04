package com.jdh.rag.service;

import com.jdh.rag.config.RagProperties;
import com.jdh.rag.domain.HybridSearchRequest;
import com.jdh.rag.domain.SearchHit;
import com.jdh.rag.port.KeywordSearchPort;
import com.jdh.rag.port.RerankPort;
import com.jdh.rag.port.VectorSearchPort;
import com.jdh.rag.support.RrfRankFusion;
import com.jdh.rag.support.SearchLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

    @Mock private KeywordSearchPort keywordSearchPort;
    @Mock private VectorSearchPort  vectorSearchPort;
    @Mock private RerankPort        rerankPort;
    @Mock private SearchLogger      searchLogger;

    private RrfRankFusion rrfRankFusion;
    private HybridSearchService service;

    @BeforeEach
    void setUp() {
        RagProperties props = mock(RagProperties.class);
        when(props.rrfK()).thenReturn(60);
        rrfRankFusion = new RrfRankFusion(props);
        service = new HybridSearchService(
                keywordSearchPort, vectorSearchPort, rerankPort, rrfRankFusion, searchLogger
        );
    }

    @Test
    @DisplayName("keyword와 vector 검색을 각각 호출한다")
    void keyword와_vector_검색을_각각_호출한다() {
        HybridSearchRequest req = req("세금 계산", 10, 10, 5, 0.6);
        when(keywordSearchPort.search(any(), anyInt(), any())).thenReturn(List.of());
        when(vectorSearchPort.search(any(), anyInt(), any(), any())).thenReturn(List.of());
        when(rerankPort.rerank(any(), any(), anyInt(), anyBoolean())).thenReturn(List.of());

        service.search(req, "req-001");

        verify(keywordSearchPort, times(1)).search(eq("세금 계산"), eq(10), any());
        verify(vectorSearchPort, times(1)).search(eq("세금 계산"), eq(10), eq(0.6), any());
    }

    @Test
    @DisplayName("RRF 결합 후 rerankPort를 호출한다")
    void RRF_결합_후_rerankPort를_호출한다() {
        HybridSearchRequest req = req("질의", 10, 10, 3, 0.6);
        when(keywordSearchPort.search(any(), anyInt(), any()))
                .thenReturn(List.of(hit("A"), hit("B")));
        when(vectorSearchPort.search(any(), anyInt(), any(), any()))
                .thenReturn(List.of(hit("B"), hit("C")));

        List<SearchHit> rerankOutput = List.of(hit("B"), hit("A"), hit("C"));
        when(rerankPort.rerank(any(), any(), eq(3), anyBoolean())).thenReturn(rerankOutput);

        List<SearchHit> result = service.search(req, "req-002");

        verify(rerankPort, times(1)).rerank(eq("질의"), any(), eq(3), anyBoolean());
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("검색 로그를 적재한다")
    void 검색_로그를_적재한다() {
        HybridSearchRequest req = req("로그 테스트", 5, 5, 2, 0.6);
        when(keywordSearchPort.search(any(), anyInt(), any())).thenReturn(List.of(hit("X")));
        when(vectorSearchPort.search(any(), anyInt(), any(), any())).thenReturn(List.of());
        when(rerankPort.rerank(any(), any(), anyInt(), anyBoolean())).thenReturn(List.of(hit("X")));

        service.search(req, "req-003");

        verify(searchLogger, times(1)).logBatch(eq("req-003"), eq("로그 테스트"), any(), any());
    }

    @Test
    @DisplayName("keyword 검색 실패 시 vector 결과만으로 degrade된다")
    void keyword_검색_실패시_vector_결과만으로_degrade된다() {
        HybridSearchRequest req = req("질의", 5, 5, 3, 0.6);
        when(keywordSearchPort.search(any(), anyInt(), any()))
                .thenThrow(new RuntimeException("ES 연결 실패"));
        when(vectorSearchPort.search(any(), anyInt(), any(), any()))
                .thenReturn(List.of(hit("V1"), hit("V2")));
        when(rerankPort.rerank(any(), any(), anyInt(), anyBoolean()))
                .thenAnswer(inv -> ((List<?>) inv.getArgument(1)).stream().limit(3).toList());

        List<SearchHit> result = service.search(req, "req-004");

        // 예외가 전파되지 않고 vector 결과로 degrade
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("vector 검색 실패 시 keyword 결과만으로 degrade된다")
    void vector_검색_실패시_keyword_결과만으로_degrade된다() {
        HybridSearchRequest req = req("질의", 5, 5, 3, 0.6);
        when(keywordSearchPort.search(any(), anyInt(), any()))
                .thenReturn(List.of(hit("K1"), hit("K2")));
        when(vectorSearchPort.search(any(), anyInt(), any(), any()))
                .thenThrow(new RuntimeException("VectorStore 연결 실패"));
        when(rerankPort.rerank(any(), any(), anyInt(), anyBoolean()))
                .thenAnswer(inv -> ((List<?>) inv.getArgument(1)).stream().limit(3).toList());

        List<SearchHit> result = service.search(req, "req-005");

        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("검색 결과 없으면 빈 목록 반환")
    void 검색_결과_없으면_빈_목록_반환() {
        HybridSearchRequest req = req("없는 질의", 5, 5, 3, 0.6);
        when(keywordSearchPort.search(any(), anyInt(), any())).thenReturn(List.of());
        when(vectorSearchPort.search(any(), anyInt(), any(), any())).thenReturn(List.of());
        when(rerankPort.rerank(any(), any(), anyInt(), anyBoolean())).thenReturn(List.of());

        List<SearchHit> result = service.search(req, "req-006");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("필터가 각 port에 전달된다")
    void 필터가_각_port에_전달된다() {
        Map<String, Object> filters = Map.of("tenantId", "tenant-A", "domain", "tax");
        HybridSearchRequest req = HybridSearchRequest.builder()
                .query("질의").topNKeyword(5).topNVector(5)
                .topKFinal(3).vectorThreshold(0.6)
                .filters(filters)
                .build();

        when(keywordSearchPort.search(any(), anyInt(), any())).thenReturn(List.of());
        when(vectorSearchPort.search(any(), anyInt(), any(), any())).thenReturn(List.of());
        when(rerankPort.rerank(any(), any(), anyInt(), anyBoolean())).thenReturn(List.of());

        service.search(req, "req-007");

        verify(keywordSearchPort).search(any(), anyInt(), eq(filters));
        verify(vectorSearchPort).search(any(), anyInt(), any(), eq(filters));
    }

    private HybridSearchRequest req(String query, int topNK, int topNV, int topK, double threshold) {
        return HybridSearchRequest.builder()
                .query(query)
                .topNKeyword(topNK).topNVector(topNV)
                .topKFinal(topK)
                .vectorThreshold(threshold)
                .filters(Map.of())
                .build();
    }

    private SearchHit hit(String id) {
        return SearchHit.of(id, "doc-" + id, "content " + id, Map.of(), "fused");
    }
}
