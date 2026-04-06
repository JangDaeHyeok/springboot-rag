package com.jdh.rag.service;

import com.jdh.rag.domain.DocumentInfo;
import com.jdh.rag.exception.common.RagException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;
import com.jdh.rag.port.DocumentManagementPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 문서 관리 서비스.
 * IngestionService가 수집(쓰기)을 담당하듯, DocumentService는 조회·삭제를 담당한다.
 *
 * 삭제 시 vector_store 와 rag_chunks 양쪽 정리는 DocumentManagementPort 구현체가 처리한다.
 * (PgDocumentAdapter: 두 저장소 모두 삭제 / InMemoryKeywordSearchAdapter: 인메모리 저장소만 삭제)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentManagementPort documentManagementPort;

    public List<DocumentInfo> listDocuments(String tenantId, String domain) {
        return documentManagementPort.listDocuments(tenantId, domain);
    }

    public DocumentInfo getDocument(String docId) {
        return documentManagementPort.findByDocId(docId)
                .orElseThrow(() -> new RagException(RagExceptionEnum.DOCUMENT_NOT_FOUND,
                        "docId=" + docId));
    }

    public void deleteDocument(String docId) {
        if (!documentManagementPort.existsByDocId(docId)) {
            throw new RagException(RagExceptionEnum.DOCUMENT_NOT_FOUND, "docId=" + docId);
        }
        documentManagementPort.deleteByDocId(docId);
        log.info("문서 삭제 완료: docId={}", docId);
    }
}