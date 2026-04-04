package com.jdh.rag.controller;

import com.jdh.rag.domain.RagAnswerResponse;
import com.jdh.rag.exception.common.RagException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;
import com.jdh.rag.service.RagAnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * RAG 질의응답 API.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rag")
public class RagController {

    private final RagAnswerService ragAnswerService;

    /**
     * POST /api/rag/answer
     *
     * <pre>
     * {
     *   "query":    "차량 매각 시 세금 계산 방법",
     *   "tenantId": "default",
     *   "domain":   "tax"
     * }
     * </pre>
     */
    @PostMapping("/answer")
    public ResponseEntity<RagAnswerResponse> answer(@RequestBody AnswerRequest request) {
        if (request.query() == null || request.query().isBlank()) {
            throw new RagException(RagExceptionEnum.BAD_REQUEST, "질의(query)는 필수입니다.");
        }

        Map<String, Object> filters = buildFilters(request);
        boolean sortByLatest = Boolean.TRUE.equals(request.sortByLatest());
        RagAnswerResponse response = ragAnswerService.answer(request.query(), filters, sortByLatest);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> buildFilters(AnswerRequest request) {
        Map<String, Object> filters = new HashMap<>();
        if (request.tenantId() != null) filters.put("tenantId", request.tenantId());
        if (request.domain()   != null) filters.put("domain",   request.domain());
        return filters;
    }

    public record AnswerRequest(
            String query,
            String tenantId,
            String domain,
            Boolean sortByLatest    // true면 RRF 이후 createdAt 내림차순 재정렬, null/false면 RRF 점수 순 유지
    ) {}
}