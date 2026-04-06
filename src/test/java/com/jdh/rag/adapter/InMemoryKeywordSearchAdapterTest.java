package com.jdh.rag.adapter;

import com.jdh.rag.domain.DocumentInfo;
import com.jdh.rag.domain.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryKeywordSearchAdapterTest {

    private InMemoryKeywordSearchAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InMemoryKeywordSearchAdapter();
    }

    // ── index / search ────────────────────────────────────────────────────────

    @Test
    @DisplayName("색인 후 쿼리 텀 포함 문서가 검색된다")
    void 색인_후_쿼리_텀_포함_문서가_검색된다() {
        adapter.index(List.of(
                hit("chunk-1", "doc-A", "연차 신청 방법 안내"),
                hit("chunk-2", "doc-B", "급여 명세서 조회")
        ));

        List<SearchHit> results = adapter.search("연차", 10, Map.of());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("chunk-1");
    }

    @Test
    @DisplayName("쿼리 텀이 없는 문서는 검색 결과에서 제외된다")
    void 쿼리_텀_없는_문서는_검색_결과에서_제외된다() {
        adapter.index(List.of(hit("chunk-1", "doc-A", "급여 명세서 조회")));

        List<SearchHit> results = adapter.search("연차", 10, Map.of());

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("topN으로 결과 수가 제한된다")
    void topN으로_결과_수가_제한된다() {
        adapter.index(List.of(
                hit("c1", "d1", "연차 연차 연차"),
                hit("c2", "d2", "연차 연차"),
                hit("c3", "d3", "연차")
        ));

        List<SearchHit> results = adapter.search("연차", 2, Map.of());

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("TF 점수가 높은 문서가 먼저 반환된다")
    void TF_점수_높은_문서가_먼저_반환된다() {
        adapter.index(List.of(
                hit("c1", "d1", "연차"),
                hit("c2", "d2", "연차 연차 연차")
        ));

        List<SearchHit> results = adapter.search("연차", 10, Map.of());

        assertThat(results.get(0).id()).isEqualTo("c2");
    }

    @Test
    @DisplayName("빈 쿼리는 빈 결과를 반환한다")
    void 빈_쿼리는_빈_결과를_반환한다() {
        adapter.index(List.of(hit("c1", "d1", "연차 신청")));

        assertThat(adapter.search("", 10, Map.of())).isEmpty();
        assertThat(adapter.search(null, 10, Map.of())).isEmpty();
        assertThat(adapter.search("   ", 10, Map.of())).isEmpty();
    }

    @Test
    @DisplayName("필터(tenantId)가 적용된다")
    void 필터_tenantId가_적용된다() {
        adapter.index(List.of(
                hitWithMeta("c1", "d1", "연차 신청", Map.of("tenantId", "tenant-A")),
                hitWithMeta("c2", "d2", "연차 조회", Map.of("tenantId", "tenant-B"))
        ));

        List<SearchHit> results = adapter.search("연차", 10, Map.of("tenantId", "tenant-A"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("c1");
    }

    @Test
    @DisplayName("검색 결과의 channel이 lexical이다")
    void 검색_결과의_channel이_lexical이다() {
        adapter.index(List.of(hit("c1", "d1", "연차")));

        List<SearchHit> results = adapter.search("연차", 10, Map.of());

        assertThat(results.get(0).channel()).isEqualTo("lexical");
    }

    // ── DocumentManagementPort ────────────────────────────────────────────────

    @Test
    @DisplayName("색인된 docId가 존재한다고 반환한다")
    void 색인된_docId가_존재한다고_반환한다() {
        adapter.index(List.of(hit("c1", "doc-A", "내용")));

        assertThat(adapter.existsByDocId("doc-A")).isTrue();
        assertThat(adapter.existsByDocId("doc-없음")).isFalse();
    }

    @Test
    @DisplayName("listDocuments가 색인된 문서 목록을 반환한다")
    void listDocuments가_색인된_문서_목록을_반환한다() {
        adapter.index(List.of(
                hit("c1", "doc-A", "내용1"),
                hit("c2", "doc-A", "내용2"),
                hit("c3", "doc-B", "내용3")
        ));

        List<DocumentInfo> docs = adapter.listDocuments(null, null);

        assertThat(docs).hasSize(2);
        assertThat(docs).extracting(DocumentInfo::docId).containsExactlyInAnyOrder("doc-A", "doc-B");
        DocumentInfo docA = docs.stream().filter(d -> "doc-A".equals(d.docId())).findFirst().orElseThrow();
        assertThat(docA.chunkCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("listDocuments domain 필터가 동작한다")
    void listDocuments_domain_필터가_동작한다() {
        adapter.index(List.of(
                hitWithMeta("c1", "doc-A", "내용", Map.of("domain", "tax")),
                hitWithMeta("c2", "doc-B", "내용", Map.of("domain", "hr"))
        ));

        List<DocumentInfo> docs = adapter.listDocuments(null, "tax");

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).docId()).isEqualTo("doc-A");
    }

    @Test
    @DisplayName("deleteByDocId가 해당 문서의 모든 청크를 제거한다")
    void deleteByDocId가_해당_문서의_모든_청크를_제거한다() {
        adapter.index(List.of(
                hit("c1", "doc-A", "연차1"),
                hit("c2", "doc-A", "연차2"),
                hit("c3", "doc-B", "급여")
        ));

        adapter.deleteByDocId("doc-A");

        assertThat(adapter.existsByDocId("doc-A")).isFalse();
        assertThat(adapter.existsByDocId("doc-B")).isTrue();
        assertThat(adapter.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("deleteByDocId 후 검색에서도 제외된다")
    void deleteByDocId_후_검색에서도_제외된다() {
        adapter.index(List.of(hit("c1", "doc-A", "연차 신청")));
        adapter.deleteByDocId("doc-A");

        List<SearchHit> results = adapter.search("연차", 10, Map.of());

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 docId 삭제는 오류 없이 완료된다")
    void 존재하지_않는_docId_삭제는_오류_없이_완료된다() {
        adapter.index(List.of(hit("c1", "doc-A", "내용")));

        adapter.deleteByDocId("doc-없음");

        assertThat(adapter.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("clear 후 인덱스가 비어있다")
    void clear_후_인덱스가_비어있다() {
        adapter.index(List.of(hit("c1", "d1", "내용")));
        adapter.clear();

        assertThat(adapter.size()).isEqualTo(0);
        assertThat(adapter.search("내용", 10, Map.of())).isEmpty();
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private SearchHit hit(String id, String docId, String content) {
        return new SearchHit(id, docId, content, Map.of("docId", docId), null, "lexical");
    }

    private SearchHit hitWithMeta(String id, String docId, String content, Map<String, Object> extraMeta) {
        java.util.Map<String, Object> meta = new java.util.HashMap<>(extraMeta);
        meta.put("docId", docId);
        return new SearchHit(id, docId, content, meta, null, "lexical");
    }
}
