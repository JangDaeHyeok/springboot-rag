package com.jdh.rag.service;

import com.jdh.rag.domain.IngestionRequest;
import com.jdh.rag.domain.IngestionResult;
import com.jdh.rag.domain.SearchHit;
import com.jdh.rag.exception.IngestionException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;
import com.jdh.rag.port.ChunkSplitterPort;
import com.jdh.rag.port.KeywordIndexPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ingestion 파이프라인:
 * 원문 로드 → 청킹 → 임베딩(VectorStore 내부) → upsert + 키워드 색인
 *
 * 메타데이터 스키마는 ingestion 시점에 확정됨 (나중에 바꾸면 재색인).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final VectorStore vectorStore;
    private final KeywordIndexPort keywordIndexPort;
    private final ChunkSplitterPort chunkSplitterPort;

    // ── 텍스트 직접 수집 ───────────────────────────────────────────────────────

    /**
     * 텍스트 콘텐츠를 직접 받아 수집한다.
     */
    public IngestionResult ingest(IngestionRequest request) {
        if (request.content() == null || request.content().isBlank()) {
            throw new IngestionException(RagExceptionEnum.EMPTY_CONTENT);
        }
        Document doc = new Document(request.content(), buildBaseMeta(request));
        return processDocuments(List.of(doc), request.docId());
    }

    // ── 파일(PDF/HTML/DOCX 등) 수집 ───────────────────────────────────────────

    /**
     * Apache Tika로 파일을 파싱하여 수집한다.
     * Spring AI TikaDocumentReader가 PDF/HTML/DOCX 등을 지원한다.
     *
     * @param resource  파일 리소스 (ClassPathResource, FileSystemResource, UrlResource 등)
     * @param request   docId / source / domain / version / tenantId 메타데이터
     */
    public IngestionResult ingestResource(Resource resource, IngestionRequest request) {
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> rawDocs;
        try {
            rawDocs = reader.get();
        } catch (Exception e) {
            log.error("파일 파싱 실패: resource={}, msg={}", resource.getFilename(), e.getMessage());
            throw new IngestionException(RagExceptionEnum.FILE_PARSE_FAILED,
                    "파일 파싱에 실패하였습니다: " + resource.getFilename(), e);
        }
        if (rawDocs == null || rawDocs.isEmpty()) {
            throw new IngestionException(RagExceptionEnum.EMPTY_CONTENT,
                    "파일에서 텍스트를 추출할 수 없습니다: " + resource.getFilename());
        }

        // 기본 메타데이터 주입
        Map<String, Object> baseMeta = buildBaseMeta(request);
        List<Document> docsWithMeta = rawDocs.stream()
                .map(d -> {
                    Map<String, Object> meta = new HashMap<>(d.getMetadata());
                    meta.putAll(baseMeta);
                    return new Document(d.getText(), meta);
                })
                .toList();

        return processDocuments(docsWithMeta, request.docId());
    }

    // ── 내부 처리 ──────────────────────────────────────────────────────────────

    private IngestionResult processDocuments(List<Document> docs, String docId) {
        // 1) 청킹 (전략은 ChunkSplitterPort 구현체가 결정: fixed | semantic)
        List<Document> chunks = chunkSplitterPort.split(docs);

        // 2) chunkId 주입
        List<Document> annotated = annotateChunks(chunks, docId);

        // 3) VectorStore upsert (임베딩은 Spring AI가 내부적으로 수행)
        try {
            vectorStore.add(annotated);
        } catch (Exception e) {
            log.error("벡터 저장소 저장 실패: docId={}, msg={}", docId, e.getMessage());
            throw new IngestionException(RagExceptionEnum.VECTOR_STORE_FAILED,
                    "벡터 저장소 저장에 실패하였습니다: docId=" + docId, e);
        }

        // 4) 키워드 인덱스
        List<SearchHit> hits = toSearchHits(annotated);
        try {
            keywordIndexPort.index(hits);
        } catch (Exception e) {
            rollbackVectorStore(annotated, docId, e);
            throw new IngestionException(RagExceptionEnum.INGESTION_FAILED,
                    "키워드 색인에 실패하였습니다. 저장된 벡터 데이터는 롤백되었습니다: docId=" + docId, e);
        }

        log.info("수집 완료: docId={}, chunks={}", docId, annotated.size());
        return new IngestionResult(docId, annotated.size());
    }

    private void rollbackVectorStore(List<Document> annotated, String docId, Exception cause) {
        List<String> ids = annotated.stream()
                .map(Document::getId)
                .toList();
        try {
            vectorStore.delete(ids);
            log.warn("키워드 색인 실패로 벡터 저장 롤백: docId={}, chunks={}, reason={}",
                    docId, ids.size(), cause.getMessage());
        } catch (Exception rollbackError) {
            log.error("벡터 저장 롤백 실패: docId={}, msg={}", docId, rollbackError.getMessage(), rollbackError);
        }
    }

    private List<Document> annotateChunks(List<Document> chunks, String docId) {
        List<Document> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> meta = new HashMap<>(chunk.getMetadata());
            meta.put("chunkId", docId + "-" + i);
            result.add(new Document(docId + "-" + i, chunk.getText(), meta));
        }
        return result;
    }

    private Map<String, Object> buildBaseMeta(IngestionRequest req) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("docId",    req.docId());
        meta.put("source",   req.source());
        meta.put("domain",   req.domain());
        meta.put("version",  req.version());
        meta.put("tenantId", req.tenantId());
        meta.put("createdAt", Instant.now().toString());
        return meta;
    }

    private List<SearchHit> toSearchHits(List<Document> docs) {
        return docs.stream()
                .map(d -> new SearchHit(
                        metaStr(d, "chunkId", d.getId()),
                        metaStr(d, "docId",   ""),
                        d.getText(),
                        d.getMetadata(),
                        null,
                        "lexical"
                ))
                .toList();
    }

    private String metaStr(Document doc, String key, String fallback) {
        Object v = doc.getMetadata().get(key);
        return v != null ? v.toString() : fallback;
    }
}
