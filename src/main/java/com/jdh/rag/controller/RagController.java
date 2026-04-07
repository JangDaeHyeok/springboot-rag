package com.jdh.rag.controller;

import com.jdh.rag.config.RagProperties;
import com.jdh.rag.domain.RagAnswerRequest;
import com.jdh.rag.domain.RagAnswerResponse;
import com.jdh.rag.exception.common.RagException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;
import com.jdh.rag.service.FeedbackService;
import com.jdh.rag.service.RagAnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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
    private final FeedbackService  feedbackService;
    private final RagProperties    ragProperties;

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
        validateQuery(request.query());
        RagAnswerResponse response = ragAnswerService.answer(toRagAnswerRequest(request));
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/rag/answer/stream  — Server-Sent Events 스트리밍 답변.
     *
     * 이벤트 타입:
     *   token    — LLM이 생성하는 토큰 조각
     *   done     — 최종 requestId + citations (JSON)
     *   error    — 에러 발생 시 메시지
     */
    @PostMapping(value = "/answer/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answerStream(@RequestBody AnswerRequest request) {
        validateQuery(request.query());

        SseEmitter emitter = new SseEmitter(ragProperties.sseTimeoutMs());
        RagAnswerRequest ragRequest = toRagAnswerRequest(request);

        Thread.ofVirtual().start(() -> {
            try {
                RagAnswerResponse response = ragAnswerService.streamAnswer(ragRequest, token -> {
                    try {
                        emitter.send(SseEmitter.event().name("token").data(token));
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
                emitter.send(SseEmitter.event().name("done").data(response));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * PATCH /api/rag/feedback/{requestId}
     *
     * <pre>
     * { "accepted": true }
     * </pre>
     *
     * answer_accepted 필드를 업데이트해 cosine threshold 튜닝 데이터를 수집한다.
     */
    @PatchMapping("/feedback/{requestId}")
    public ResponseEntity<Void> submitFeedback(
            @PathVariable String requestId,
            @RequestBody FeedbackRequest request
    ) {
        if (request.accepted() == null) {
            throw new RagException(RagExceptionEnum.BAD_REQUEST, "accepted 필드는 필수입니다.");
        }
        feedbackService.submitFeedback(requestId, request.accepted());
        return ResponseEntity.noContent().build();
    }

    // ── private ───────────────────────────────────────────────────────────────

    private void validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new RagException(RagExceptionEnum.BAD_REQUEST, "질의(query)는 필수입니다.");
        }
    }

    private RagAnswerRequest toRagAnswerRequest(AnswerRequest request) {
        return new RagAnswerRequest(
                request.query(),
                buildFilters(request),
                Boolean.TRUE.equals(request.sortByLatest())
        );
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
            Boolean sortByLatest    // true면 RRF 이후 createdAt 내림차순 재정렬
    ) {}

    public record FeedbackRequest(Boolean accepted) {}
}
