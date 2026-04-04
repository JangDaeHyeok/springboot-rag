package com.jdh.rag.adapter;

import com.jdh.rag.domain.SearchLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PgSearchLogAdapterTest {

    @Mock private SearchLogJpaRepository searchLogJpaRepository;
    @Captor private ArgumentCaptor<List<SearchLogEntity>> batchCaptor;

    private PgSearchLogAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PgSearchLogAdapter(searchLogJpaRepository);
    }

    @Test
    @DisplayName("save 단건 호출 시 엔티티로 변환하여 저장한다")
    void save_단건_호출시_엔티티로_변환하여_저장한다() {
        SearchLog log = searchLog("req-1", "쿼리", "doc-A", "chunk-A-0", 0.85, 1, true, "vector");

        adapter.save(log);

        ArgumentCaptor<SearchLogEntity> captor = ArgumentCaptor.forClass(SearchLogEntity.class);
        verify(searchLogJpaRepository).save(captor.capture());

        SearchLogEntity entity = captor.getValue();
        assertThat(entity.getRequestId()).isEqualTo("req-1");
        assertThat(entity.getQuery()).isEqualTo("쿼리");
        assertThat(entity.getDocId()).isEqualTo("doc-A");
        assertThat(entity.getChunkId()).isEqualTo("chunk-A-0");
        assertThat(entity.getCosineScore()).isEqualTo(0.85);
        assertThat(entity.getRankPos()).isEqualTo(1);
        assertThat(entity.isUsedInPrompt()).isTrue();
        assertThat(entity.getChannel()).isEqualTo("vector");
        assertThat(entity.getAnswerAccepted()).isNull();
        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("saveBatch 복수 호출 시 saveAll로 일괄 저장한다")
    void saveBatch_복수_호출시_saveAll로_일괄_저장한다() {
        List<SearchLog> logs = List.of(
                searchLog("req-2", "질의", "doc-B", "chunk-B-0", 0.7, 1, true, "fused"),
                searchLog("req-2", "질의", "doc-C", "chunk-C-0", 0.6, 2, false, "fused")
        );

        adapter.saveBatch(logs);

        verify(searchLogJpaRepository).saveAll(batchCaptor.capture());
        assertThat(batchCaptor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("saveBatch 빈 목록이면 saveAll을 호출하지 않는다")
    void saveBatch_빈_목록이면_saveAll을_호출하지_않는다() {
        adapter.saveBatch(List.of());

        verifyNoInteractions(searchLogJpaRepository);
    }

    @Test
    @DisplayName("saveBatch null이면 saveAll을 호출하지 않는다")
    void saveBatch_null이면_saveAll을_호출하지_않는다() {
        adapter.saveBatch(null);

        verifyNoInteractions(searchLogJpaRepository);
    }

    @Test
    @DisplayName("searchLog createdAt이 null이면 현재 시각으로 대체한다")
    void searchLog_createdAt이_null이면_현재시각으로_대체한다() {
        SearchLog log = SearchLog.builder()
                .requestId("req-3").query("q")
                .docId("d").chunkId("c")
                .cosineScore(0.5).rank(1)
                .usedInPrompt(false).channel("lexical")
                // createdAt은 null (null 처리 테스트)
                .build();

        adapter.save(log);

        ArgumentCaptor<SearchLogEntity> captor = ArgumentCaptor.forClass(SearchLogEntity.class);
        verify(searchLogJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    private SearchLog searchLog(String requestId, String query, String docId, String chunkId,
                                 double score, int rank, boolean used, String channel) {
        return SearchLog.builder()
                .requestId(requestId).query(query)
                .docId(docId).chunkId(chunkId)
                .cosineScore(score).rank(rank)
                .usedInPrompt(used).channel(channel)
                .createdAt(Instant.now())
                .build();
    }
}
