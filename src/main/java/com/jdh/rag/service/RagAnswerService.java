package com.jdh.rag.service;

import com.jdh.rag.config.RagProperties;
import com.jdh.rag.domain.GuardrailResult;
import com.jdh.rag.domain.HybridSearchRequest;
import com.jdh.rag.domain.ProcessedQuery;
import com.jdh.rag.domain.RagAnswerRequest;
import com.jdh.rag.domain.RagAnswerResponse;
import com.jdh.rag.domain.SearchHit;
import com.jdh.rag.exception.LlmException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;
import com.jdh.rag.port.InputGuardrailPort;
import com.jdh.rag.port.OutputGuardrailPort;
import com.jdh.rag.port.QueryPreprocessPort;
import com.jdh.rag.support.ContextBuilder;
import com.jdh.rag.support.prompt.RagAnswerPrompts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * RAG 답변 생성 파이프라인:
 * 1) 입력 가드레일  (프롬프트 인젝션·범위 외 질의 차단)
 * 2) 쿼리 전처리   (keywordQuery + vectorQuery 생성)
 * 3) 하이브리드 검색 (BM25 + Vector + RRF + Rerank)
 * 4) 컨텍스트 구성  (dedup / trim / sanitize)
 * 5) ChatClient 호출
 * 6) 출력 가드레일  (환각 감지, 근거 검증)
 *
 * 스트리밍(streamAnswer)은 5번 단계를 토큰 단위로 전송하며, 출력 가드레일을 생략한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagAnswerService {

    private final HybridSearchService hybridSearchService;
    private final ContextBuilder      contextBuilder;
    private final ChatClient          chatClient;
    private final RagProperties       ragProperties;
    private final InputGuardrailPort  inputGuardrailPort;
    private final OutputGuardrailPort outputGuardrailPort;
    private final QueryPreprocessPort queryPreprocessPort;
    private final RagAnswerPrompts    prompts;

    public RagAnswerResponse answer(RagAnswerRequest request) {
        String requestId = UUID.randomUUID().toString();

        // 1) 입력 가드레일
        GuardrailResult inputCheck = inputGuardrailPort.check(request.query());
        if (inputCheck.isBlocked()) {
            log.warn("[{}] 입력 가드레일 차단: {}", requestId, inputCheck.reason());
            return new RagAnswerResponse(requestId, inputCheck.userMessage(), List.of());
        }
        if (inputCheck.isWarned()) {
            log.warn("[{}] 입력 가드레일 경고: {}", requestId, inputCheck.reason());
        }

        // 2) 쿼리 전처리
        ProcessedQuery processed = queryPreprocessPort.preprocess(request.query());
        log.info("[{}] 쿼리 전처리 완료: keyword={}, vectorLen={}",
                requestId, processed.keywordQuery(), processed.vectorQuery().length());

        // 3) 하이브리드 검색
        HybridSearchRequest searchReq = buildSearchRequest(request, processed);
        List<SearchHit> ranked = hybridSearchService.search(searchReq, requestId);

        // 4) 검색 결과 없으면 fallback
        if (ranked.isEmpty()) {
            log.warn("[{}] 검색 결과 없음 → 일반 LLM 답변", requestId);
            return answerWithoutContext(requestId, request.query());
        }

        // 5) 컨텍스트 구성
        ContextBuilder.BuiltContext built = contextBuilder.build(
                ranked, ragProperties.topKFinal(), ragProperties.maxCharsPerChunk());

        // 6) LLM 호출
        String answer;
        try {
            answer = chatClient.prompt()
                    .system(prompts.system())
                    .user(prompts.userTemplate().formatted(request.query(), built.contextText()))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("[{}] LLM 호출 실패: {}", requestId, e.getMessage());
            throw new LlmException(RagExceptionEnum.LLM_CALL_FAILED, e);
        }

        // 7) 출력 가드레일
        GuardrailResult outputCheck = outputGuardrailPort.check(answer, built.contextText());
        if (outputCheck.isBlocked()) {
            log.warn("[{}] 출력 가드레일 차단: {}", requestId, outputCheck.reason());
            return new RagAnswerResponse(requestId, outputCheck.userMessage(), built.citations());
        }
        if (outputCheck.isWarned()) {
            log.warn("[{}] 출력 가드레일 경고: {}", requestId, outputCheck.reason());
            answer = answer + "\n\n" + outputCheck.userMessage();
        }

        log.info("[{}] RAG 답변 생성 완료: citations={}건", requestId, built.citations().size());
        return new RagAnswerResponse(requestId, answer, built.citations());
    }

    /**
     * 스트리밍 답변 생성. LLM 토큰을 tokenConsumer로 즉시 전달한다.
     * 출력 가드레일은 완전한 답변이 필요하므로 스트리밍에서는 생략한다.
     *
     * @param request       답변 생성 요청
     * @param tokenConsumer 토큰 수신 콜백 (컨트롤러에서 SSE 전송)
     * @return citations 포함 최종 응답 (answer 필드는 "[streamed]"로 표시)
     */
    public RagAnswerResponse streamAnswer(RagAnswerRequest request, Consumer<String> tokenConsumer) {
        String requestId = UUID.randomUUID().toString();

        // 1) 입력 가드레일
        GuardrailResult inputCheck = inputGuardrailPort.check(request.query());
        if (inputCheck.isBlocked()) {
            log.warn("[{}] 입력 가드레일 차단 (stream): {}", requestId, inputCheck.reason());
            return new RagAnswerResponse(requestId, inputCheck.userMessage(), List.of());
        }
        if (inputCheck.isWarned()) {
            log.warn("[{}] 입력 가드레일 경고 (stream): {}", requestId, inputCheck.reason());
        }

        // 2) 쿼리 전처리
        ProcessedQuery processed = queryPreprocessPort.preprocess(request.query());

        // 3) 하이브리드 검색
        HybridSearchRequest searchReq = buildSearchRequest(request, processed);
        List<SearchHit> ranked = hybridSearchService.search(searchReq, requestId);

        // 4) 검색 결과 없으면 fallback 스트리밍
        if (ranked.isEmpty()) {
            log.warn("[{}] 검색 결과 없음 → 일반 LLM 스트리밍 답변", requestId);
            streamWithoutContext(requestId, request.query(), tokenConsumer);
            return new RagAnswerResponse(requestId, "[streamed]", List.of());
        }

        // 5) 컨텍스트 구성
        ContextBuilder.BuiltContext built = contextBuilder.build(
                ranked, ragProperties.topKFinal(), ragProperties.maxCharsPerChunk());

        // 6) LLM 스트리밍 (출력 가드레일 생략)
        try {
            chatClient.prompt()
                    .system(prompts.system())
                    .user(prompts.userTemplate().formatted(request.query(), built.contextText()))
                    .stream()
                    .content()
                    .doOnNext(token -> {
                        try { tokenConsumer.accept(token); }
                        catch (UncheckedIOException e) { throw e; }
                    })
                    .blockLast();
        } catch (Exception e) {
            log.error("[{}] LLM 스트리밍 실패: {}", requestId, e.getMessage());
            throw new LlmException(RagExceptionEnum.LLM_CALL_FAILED, e);
        }

        log.info("[{}] RAG 스트리밍 완료: citations={}건", requestId, built.citations().size());
        return new RagAnswerResponse(requestId, "[streamed]", built.citations());
    }

    // ── private ───────────────────────────────────────────────────────────────

    private HybridSearchRequest buildSearchRequest(RagAnswerRequest request, ProcessedQuery processed) {
        return HybridSearchRequest.builder()
                .query(request.query())
                .keywordQuery(processed.keywordQuery())
                .vectorQuery(processed.vectorQuery())
                .topNKeyword(ragProperties.topNKeyword())
                .topNVector(ragProperties.topNVector())
                .topKFinal(ragProperties.topKFinal())
                .vectorThreshold(ragProperties.vectorThreshold())
                .filters(request.filters())
                .sortByLatest(request.sortByLatest())
                .build();
    }

    private RagAnswerResponse answerWithoutContext(String requestId, String query) {
        String answer;
        try {
            answer = chatClient.prompt()
                    .system(prompts.fallbackSystem())
                    .user(query)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("[{}] LLM 호출 실패(컨텍스트 없음): {}", requestId, e.getMessage());
            throw new LlmException(RagExceptionEnum.LLM_CALL_FAILED, e);
        }
        return new RagAnswerResponse(requestId, answer, List.of());
    }

    private void streamWithoutContext(String requestId, String query, Consumer<String> tokenConsumer) {
        try {
            chatClient.prompt()
                    .system(prompts.fallbackSystem())
                    .user(query)
                    .stream()
                    .content()
                    .doOnNext(tokenConsumer::accept)
                    .blockLast();
        } catch (Exception e) {
            log.error("[{}] LLM 스트리밍 실패(컨텍스트 없음): {}", requestId, e.getMessage());
            throw new LlmException(RagExceptionEnum.LLM_CALL_FAILED, e);
        }
    }
}
