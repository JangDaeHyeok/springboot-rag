package com.jdh.rag.controller;

import com.jdh.rag.domain.DocumentInfo;
import com.jdh.rag.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 문서 관리 API.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    /**
     * GET /api/documents?tenantId=...&domain=...
     * 수집된 문서 목록을 반환한다.
     */
    @GetMapping
    public ResponseEntity<List<DocumentInfo>> listDocuments(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String domain
    ) {
        return ResponseEntity.ok(documentService.listDocuments(tenantId, domain));
    }

    /**
     * GET /api/documents/{docId}
     * 특정 문서 정보를 반환한다. 없으면 404.
     */
    @GetMapping("/{docId}")
    public ResponseEntity<DocumentInfo> getDocument(@PathVariable String docId) {
        return ResponseEntity.ok(documentService.getDocument(docId));
    }

    /**
     * DELETE /api/documents/{docId}
     * 문서를 rag_chunks와 vector_store에서 모두 삭제한다.
     * 재수집이 필요하면 DELETE 후 POST /api/ingest/* 를 다시 호출한다.
     */
    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String docId) {
        documentService.deleteDocument(docId);
        return ResponseEntity.noContent().build();
    }
}
