package com.jdh.rag.domain;

/**
 * 문서 수집 요청.
 * 파일 업로드 시엔 content를 비워 두고 IngestionService.ingestResource()를 사용한다.
 */
public record IngestionRequest(
        String docId,       // 재색인/삭제 단위
        String source,      // 파일명, URL 등 출처
        String domain,      // "tax" / "law" / "faq" 등
        String version,     // "2024.01" 등 정책 버전
        String tenantId,    // 단일테넌트면 "default"
        String content
) {}
