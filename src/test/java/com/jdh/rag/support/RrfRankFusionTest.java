package com.jdh.rag.support;

import com.jdh.rag.config.RagProperties;
import com.jdh.rag.domain.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RrfRankFusionTest {

    private RrfRankFusion rrfRankFusion;

    @BeforeEach
    void setUp() {
        RagProperties props = mock(RagProperties.class);
        when(props.rrfK()).thenReturn(60);
        rrfRankFusion = new RrfRankFusion(props);
    }

    @Test
    @DisplayName("두 목록 RRF 결합 순위가 올바르다")
    void 두_목록_RRF_결합_순위가_올바르다() {
        // given
        List<SearchHit> lexical = List.of(
                hit("chunk-A", "A"),
                hit("chunk-B", "B"),
                hit("chunk-C", "C")
        );
        List<SearchHit> vector = List.of(
                hit("chunk-B", "B"),  // B가 양쪽 상위 → RRF 점수 높아야 함
                hit("chunk-D", "D"),
                hit("chunk-A", "A")
        );

        // when
        List<SearchHit> result = rrfRankFusion.fuse(lexical, vector, SearchHit::id, 4);

        // then
        assertThat(result).hasSize(4);
        // B는 lexical 2위(1/62), vector 1위(1/61) → 합계 가장 높음
        assertThat(result.get(0).id()).isEqualTo("chunk-B");
        // A는 lexical 1위(1/61), vector 3위(1/63)
        assertThat(result.get(1).id()).isEqualTo("chunk-A");
    }

    @Test
    @DisplayName("topK 초과 결과는 잘린다")
    void topK_초과_결과는_잘린다() {
        List<SearchHit> listA = List.of(
                hit("1", "doc1"), hit("2", "doc2"), hit("3", "doc3")
        );
        List<SearchHit> listB = List.of(
                hit("4", "doc4"), hit("5", "doc5")
        );

        List<SearchHit> result = rrfRankFusion.fuse(listA, listB, SearchHit::id, 2);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("빈 목록이 입력되어도 오류 없이 동작한다")
    void 빈_목록이_입력되어도_오류_없이_동작한다() {
        List<SearchHit> lexical = List.of(hit("chunk-A", "A"));
        List<SearchHit> vector = List.of();

        List<SearchHit> result = rrfRankFusion.fuse(lexical, vector, SearchHit::id, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("chunk-A");
    }

    @Test
    @DisplayName("양쪽 모두 빈 목록이면 빈 결과 반환")
    void 양쪽_모두_빈_목록이면_빈_결과_반환() {
        List<SearchHit> result = rrfRankFusion.fuse(List.of(), List.of(), SearchHit::id, 5);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 목록은 빈 목록으로 처리된다")
    void null_목록은_빈_목록으로_처리된다() {
        List<SearchHit> vector = List.of(hit("chunk-X", "X"));

        List<SearchHit> result = rrfRankFusion.fuse(null, vector, SearchHit::id, 5);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("RRF 점수가 SearchHit score에 반영된다")
    void RRF_점수가_SearchHit_score에_반영된다() {
        List<SearchHit> lexical = List.of(hit("chunk-A", "A"));
        List<SearchHit> vector = List.of(hit("chunk-A", "A")); // 동일 chunk

        List<SearchHit> result = rrfRankFusion.fuse(lexical, vector, SearchHit::id, 5);

        assertThat(result.get(0).score()).isGreaterThan(0);
        // 두 목록 모두 rank=1 → score = 1/61 + 1/61 ≈ 0.0328
        assertThat(result.get(0).score()).isEqualTo(1.0 / 61 + 1.0 / 61, offset(1e-10));
    }

    @Test
    @DisplayName("결과의 channel이 fused로 설정된다")
    void 결과의_channel이_fused로_설정된다() {
        List<SearchHit> result = rrfRankFusion.fuse(
                List.of(hit("A", "docA")),
                List.of(hit("B", "docB")),
                SearchHit::id, 5
        );

        assertThat(result).allMatch(h -> "fused".equals(h.channel()));
    }

    private SearchHit hit(String id, String docId) {
        return SearchHit.of(id, docId, "content of " + id, Map.of(), "lexical");
    }
}
