package com.jdh.rag.adapter;

import com.jdh.rag.domain.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// VectorStore 인터페이스를 목킹하므로 PGVector/Milvus 구현체 변경과 무관하게 동작한다.
class SpringAiVectorSearchAdapterTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private SpringAiVectorSearchAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpringAiVectorSearchAdapter(vectorStore);
    }

    @Test
    @DisplayName("필터가 있으면 VectorStore 검색 요청에 filterExpression을 포함한다")
    void 필터가_있으면_VectorStore_검색_요청에_filterExpression을_포함한다() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        adapter.search("질의", 5, 0.6, Map.of("tenantId", "tenant-A", "domain", "tax"));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();

        assertThat(request.hasFilterExpression()).isTrue();
        assertThat(request.getFilterExpression().toString())
                .contains("tenantId")
                .contains("tenant-A")
                .contains("domain")
                .contains("tax");
    }

    @Test
    @DisplayName("메타데이터 필터와 일치하지 않는 문서는 후처리에서도 제외한다")
    void 메타데이터_필터와_일치하지_않는_문서는_후처리에서도_제외한다() {
        Document matched = new Document("chunk-1", "내용1", Map.of("chunkId", "chunk-1", "docId", "doc-1", "tenantId", "tenant-A"));
        Document filteredOut = new Document("chunk-2", "내용2", Map.of("chunkId", "chunk-2", "docId", "doc-2", "tenantId", "tenant-B"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(matched, filteredOut));

        List<SearchHit> hits = adapter.search("질의", 5, 0.6, Map.of("tenantId", "tenant-A"));

        assertThat(hits).extracting(SearchHit::id).containsExactly("chunk-1");
    }
}
