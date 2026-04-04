package com.jdh.rag.controller;

import com.jdh.rag.domain.IngestionRequest;
import com.jdh.rag.domain.IngestionResult;
import com.jdh.rag.exception.IngestionException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;
import com.jdh.rag.service.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 문서 수집(Ingestion) API.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService ingestionService;

    /**
     * POST /api/ingest/text  - 텍스트 직접 수집
     *
     * <pre>
     * {
     *   "docId":    "law-2024-vehicle-tax",
     *   "source":   "국세청_차량양도_가이드.pdf",
     *   "domain":   "tax",
     *   "version":  "2024.01",
     *   "tenantId": "default",
     *   "content":  "원문 텍스트 내용..."
     * }
     * </pre>
     */
    @PostMapping("/text")
    public ResponseEntity<IngestionResult> ingestText(@RequestBody IngestionRequest request) {
        if (request.content() == null || request.content().isBlank()) {
            throw new IngestionException(RagExceptionEnum.EMPTY_CONTENT);
        }
        IngestionResult result = ingestionService.ingest(request);
        return ResponseEntity.accepted().body(result);
    }

    /**
     * POST /api/ingest/file  - 파일 업로드 수집 (PDF, HTML, DOCX 등)
     *
     * multipart/form-data:
     *   file     = 업로드 파일
     *   docId    = 문서 ID
     *   source   = 출처
     *   domain   = 도메인
     *   version  = 버전
     *   tenantId = 테넌트 ID
     */
    @PostMapping("/file")
    public ResponseEntity<IngestionResult> ingestFile(
            @RequestParam("file")     MultipartFile file,
            @RequestParam("docId")    String docId,
            @RequestParam("source")   String source,
            @RequestParam("domain")   String domain,
            @RequestParam("version")  String version,
            @RequestParam(value = "tenantId", defaultValue = "default") String tenantId
    ) {
        if (file.isEmpty()) {
            throw new IngestionException(RagExceptionEnum.EMPTY_CONTENT, "업로드된 파일이 비어 있습니다.");
        }

        IngestionRequest request = new IngestionRequest(docId, source, domain, version, tenantId, "");
        Resource resource = file.getResource();
        IngestionResult result = ingestionService.ingestResource(resource, request);
        return ResponseEntity.accepted().body(result);
    }
}