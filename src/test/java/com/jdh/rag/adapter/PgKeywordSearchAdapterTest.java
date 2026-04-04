package com.jdh.rag.adapter;

import com.jdh.rag.domain.SearchHit;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PgKeywordSearchAdapterTest {

    @Mock private RagChunkJpaRepository ragChunkJpaRepository;
    @Captor private ArgumentCaptor<List<RagChunkEntity>> chunkCaptor;

    private PgKeywordSearchAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PgKeywordSearchAdapter(ragChunkJpaRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("BM25 검색 결과를 SearchHit 목록으로 변환한다")
    void BM25_결과를_SearchHit_목록으로_변환한다() {
        Object[] row = {
                "doc-A-0", "doc-A", "세금 내용 설명", "{\"docId\":\"doc-A\"}", 0.42
        };
        when(ragChunkJpaRepository.findByPgSearch(eq("세금"), isNull(), isNull(), eq(10)))
                .thenReturn(List.<Object[]>of(row));

        List<SearchHit> hits = adapter.search("세금", 10, Map.of());

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).id()).isEqualTo("doc-A-0");
        assertThat(hits.get(0).docId()).isEqualTo("doc-A");
        assertThat(hits.get(0).score()).isEqualTo(0.42);
        assertThat(hits.get(0).channel()).isEqualTo("lexical");
    }

    @Test
    @DisplayName("tenantId 필터를 레포지토리에 전달한다")
    void tenantId_필터를_레포지토리에_전달한다() {
        when(ragChunkJpaRepository.findByPgSearch(any(), eq("tenant-A"), isNull(), anyInt()))
                .thenReturn(List.of());

        adapter.search("질의", 5, Map.of("tenantId", "tenant-A"));

        verify(ragChunkJpaRepository).findByPgSearch("질의", "tenant-A", null, 5);
    }

    @Test
    @DisplayName("domain 필터를 레포지토리에 전달한다")
    void domain_필터를_레포지토리에_전달한다() {
        when(ragChunkJpaRepository.findByPgSearch(any(), isNull(), eq("tax"), anyInt()))
                .thenReturn(List.of());

        adapter.search("질의", 5, Map.of("domain", "tax"));

        verify(ragChunkJpaRepository).findByPgSearch("질의", null, "tax", 5);
    }

    @Test
    @DisplayName("빈 query이면 레포지토리를 호출하지 않는다")
    void 빈_query이면_레포지토리를_호출하지_않는다() {
        List<SearchHit> hits = adapter.search("", 10, Map.of());

        assertThat(hits).isEmpty();
        verifyNoInteractions(ragChunkJpaRepository);
    }

    @Test
    @DisplayName("null query이면 레포지토리를 호출하지 않는다")
    void null_query이면_레포지토리를_호출하지_않는다() {
        List<SearchHit> hits = adapter.search(null, 10, Map.of());

        assertThat(hits).isEmpty();
        verifyNoInteractions(ragChunkJpaRepository);
    }

    @Test
    @DisplayName("metadata JSON 파싱 실패 시 빈 map을 반환한다")
    void metadata_JSON_파싱_실패시_빈_map을_반환한다() {
        Object[] row = {"chunk-1", "doc-1", "내용", "invalid-json{{{", 0.5};
        when(ragChunkJpaRepository.findByPgSearch(any(), any(), any(), anyInt()))
                .thenReturn(List.<Object[]>of(row));

        List<SearchHit> hits = adapter.search("질의", 10, Map.of());

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).meta()).isEmpty();
    }

    @Test
    @DisplayName("index 호출 시 saveAll을 실행한다")
    void index_호출시_saveAll을_실행한다() {
        List<SearchHit> hits = List.of(
                new SearchHit("doc-A-0", "doc-A", "내용1", Map.of("tenantId", "t1"), null, "lexical"),
                new SearchHit("doc-A-1", "doc-A", "내용2", Map.of("domain", "tax"), null, "lexical")
        );

        adapter.index(hits);

        verify(ragChunkJpaRepository, times(1)).saveAll(chunkCaptor.capture());
        assertThat(chunkCaptor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("빈 목록으로 index 호출 시 saveAll을 호출하지 않는다")
    void 빈_목록으로_index_호출시_saveAll을_호출하지_않는다() {
        adapter.index(List.of());

        verifyNoInteractions(ragChunkJpaRepository);
    }

    @Test
    @DisplayName("null 목록으로 index 호출 시 saveAll을 호출하지 않는다")
    void null_목록으로_index_호출시_saveAll을_호출하지_않는다() {
        adapter.index(null);

        verifyNoInteractions(ragChunkJpaRepository);
    }

    @Test
    @DisplayName("index 엔티티 필드가 SearchHit 메타에서 매핑된다")
    void index_엔티티_필드가_SearchHit_메타에서_매핑된다() {
        Map<String, Object> meta = Map.of(
                "tenantId", "t1", "domain", "tax",
                "version", "2024.01", "source", "doc.pdf"
        );
        SearchHit hit = new SearchHit("chunk-id", "doc-id", "청크 내용", meta, null, "lexical");

        adapter.index(List.of(hit));

        verify(ragChunkJpaRepository).saveAll(chunkCaptor.capture());
        RagChunkEntity entity = chunkCaptor.getValue().get(0);
        assertThat(entity.getChunkId()).isEqualTo("chunk-id");
        assertThat(entity.getDocId()).isEqualTo("doc-id");
        assertThat(entity.getTenantId()).isEqualTo("t1");
        assertThat(entity.getDomain()).isEqualTo("tax");
        assertThat(entity.getVersion()).isEqualTo("2024.01");
        assertThat(entity.getSource()).isEqualTo("doc.pdf");
    }
}
