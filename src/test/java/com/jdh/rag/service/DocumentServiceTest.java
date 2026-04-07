package com.jdh.rag.service;

import com.jdh.rag.domain.DocumentInfo;
import com.jdh.rag.exception.common.RagException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;
import com.jdh.rag.port.DocumentManagementPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock  private DocumentManagementPort documentManagementPort;
    @InjectMocks private DocumentService  documentService;

    // ── listDocuments ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("listDocuments는 포트 반환값을 그대로 전달한다")
    void listDocuments는_포트_반환값을_그대로_전달한다() {
        // given
        List<DocumentInfo> expected = List.of(doc("doc-A"), doc("doc-B"));
        when(documentManagementPort.listDocuments("tenant-1", "tax")).thenReturn(expected);

        // when
        List<DocumentInfo> result = documentService.listDocuments("tenant-1", "tax");

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("tenantId, domain이 null이면 포트에 null로 전달된다")
    void tenantId_domain이_null이면_포트에_null로_전달된다() {
        // given
        when(documentManagementPort.listDocuments(null, null)).thenReturn(List.of());

        // when
        documentService.listDocuments(null, null);

        // then
        verify(documentManagementPort).listDocuments(null, null);
    }

    // ── getDocument ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하는 docId이면 DocumentInfo를 반환한다")
    void 존재하는_docId이면_DocumentInfo를_반환한다() {
        // given
        DocumentInfo expected = doc("doc-A");
        when(documentManagementPort.findByDocId("doc-A")).thenReturn(Optional.of(expected));

        // when
        DocumentInfo result = documentService.getDocument("doc-A");

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("존재하지 않는 docId이면 DOCUMENT_NOT_FOUND 예외를 던진다")
    void 존재하지_않는_docId이면_DOCUMENT_NOT_FOUND_예외를_던진다() {
        // given
        when(documentManagementPort.findByDocId("doc-없음")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> documentService.getDocument("doc-없음"))
                .isInstanceOf(RagException.class)
                .extracting(e -> ((RagException) e).getExceptionEnum())
                .isEqualTo(RagExceptionEnum.DOCUMENT_NOT_FOUND);
    }

    // ── deleteDocument ────────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하는 docId이면 deleteByDocId를 호출한다")
    void 존재하는_docId이면_deleteByDocId를_호출한다() {
        // given
        when(documentManagementPort.existsByDocId("doc-A")).thenReturn(true);

        // when
        documentService.deleteDocument("doc-A");

        // then
        verify(documentManagementPort).deleteByDocId("doc-A");
    }

    @Test
    @DisplayName("존재하지 않는 docId이면 DOCUMENT_NOT_FOUND 예외를 던진다")
    void 존재하지_않는_docId이면_삭제시_DOCUMENT_NOT_FOUND_예외를_던진다() {
        // given
        when(documentManagementPort.existsByDocId("doc-없음")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> documentService.deleteDocument("doc-없음"))
                .isInstanceOf(RagException.class)
                .extracting(e -> ((RagException) e).getExceptionEnum())
                .isEqualTo(RagExceptionEnum.DOCUMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("존재 확인 후 삭제를 호출한다 (순서 보장)")
    void 존재_확인_후_삭제를_호출한다() {
        // given
        when(documentManagementPort.existsByDocId("doc-A")).thenReturn(false);

        // when
        try { documentService.deleteDocument("doc-A"); } catch (RagException ignored) {}

        // then: 존재하지 않으면 deleteByDocId 호출하지 않는다
        verify(documentManagementPort, never()).deleteByDocId(org.mockito.ArgumentMatchers.any());
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private DocumentInfo doc(String docId) {
        return new DocumentInfo(docId, docId + ".pdf", "tax", "1.0", "tenant-1", 3, null);
    }
}
