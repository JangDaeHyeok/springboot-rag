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
 * PostgreSQL(BM25) + Milvus(Vector) 기반 문서 관리 어댑터.
 * rag_chunks(BM25 인덱스)와 Milvus VectorStore 양쪽을 모두 삭제한다.
 *
 * 벡터 삭제 시 rag_chunks.chunk_id 를 Milvus primary key로 활용한다.
 * IngestionService 에서 Document.id = chunkId 로 저장하므로 1:1 대응이 보장된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.keyword-search-type", havingValue = "postgres")
public class PgDocumentAdapter implements DocumentManagementPort {

    private final RagChunkJpaRepository ragChunkJpaRepository;
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
        // 1) Milvus 벡터 삭제: rag_chunks의 chunkId = Milvus 컬렉션 primary key
        List<String> chunkIds = ragChunkJpaRepository.findChunkIdsByDocId(docId);
        if (!chunkIds.isEmpty()) {
            vectorStore.delete(chunkIds);
            log.info("Milvus에서 docId={} 삭제: {}건", docId, chunkIds.size());
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