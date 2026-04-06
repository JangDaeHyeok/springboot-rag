package com.jdh.rag.port;

import com.jdh.rag.domain.DocumentInfo;

import java.util.List;
import java.util.Optional;

/**
 * 문서 관리 포트.
 * 수집된 문서의 존재 확인, 목록 조회, 삭제를 추상화한다.
 * rag_chunks 기준으로 동작하며, vector_store 삭제는 DocumentService에서 VectorStore 직접 처리.
 */
public interface DocumentManagementPort {

    boolean existsByDocId(String docId);

    Optional<DocumentInfo> findByDocId(String docId);

    List<DocumentInfo> listDocuments(String tenantId, String domain);

    void deleteByDocId(String docId);
}
