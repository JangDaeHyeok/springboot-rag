package com.jdh.rag.adapter;

import com.jdh.rag.domain.DocumentInfo;
import com.jdh.rag.port.DocumentManagementPort;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL 기반 문서 관리 어댑터.
 * rag_chunks 와 vector_store 양쪽을 모두 삭제한다.
 * (vector_store 는 Spring AI가 관리하는 테이블이므로 VectorStoreRepository로 ID를 조회한 뒤 VectorStore.delete()로 제거한다.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.keyword-search-type", havingValue = "postgres")
public class PgDocumentAdapter implements DocumentManagementPort {

    private final RagChunkJpaRepository ragChunkJpaRepository;
    private final VectorStoreRepository vectorStoreRepository;
    private final VectorStore           vectorStore;

    @Override
    public boolean existsByDocId(String docId) {
        return ragChunkJpaRepository.existsByDocId(docId);
    }

    @Override
    public Optional<DocumentInfo> findByDocId(String docId) {
        return ragChunkJpaRepository.findDocumentInfoByDocId(docId)
                .stream().findFirst().map(this::toDocumentInfo);
    }

    @Override
    public List<DocumentInfo> listDocuments(String tenantId, String domain) {
        return ragChunkJpaRepository.findDocumentInfos(tenantId, domain)
                .stream().map(this::toDocumentInfo).toList();
    }

    @Override
    @Transactional
    public void deleteByDocId(String docId) {
        // 1) vector_store 삭제 (Spring AI 관리 테이블)
        List<String> vsIds = vectorStoreRepository.findIdsByDocId(docId);
        if (!vsIds.isEmpty()) {
            vectorStore.delete(vsIds);
            log.info("vector_store에서 docId={} 삭제: {}건", docId, vsIds.size());
        }
        // 2) rag_chunks 삭제 (BM25 인덱스 포함)
        ragChunkJpaRepository.deleteByDocId(docId);
        log.info("rag_chunks에서 docId={} 삭제 완료", docId);
    }

    private DocumentInfo toDocumentInfo(Object[] row) {
        return new DocumentInfo(
                RowMapper.str(row[0]),
                RowMapper.str(row[1]),
                RowMapper.str(row[2]),
                RowMapper.str(row[3]),
                RowMapper.str(row[4]),
                RowMapper.toLong(row[5]),
                row[6] instanceof Instant i ? i : null
        );
    }
}
