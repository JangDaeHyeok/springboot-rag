package com.jdh.rag.service;

import com.jdh.rag.config.RagProperties;
import com.jdh.rag.domain.GuardrailResult;
import com.jdh.rag.domain.HybridSearchRequest;
import com.jdh.rag.domain.ProcessedQuery;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RAG 답변 생성 파이프라인:
 * 1) 입력 가드레일  (프롬프트 인젝션·범위 외 질의 차단)
 * 2) 쿼리 전처리   (BM25용 keywordQuery + Vector용 vectorQuery 생성)
 * 3) 하이브리드 검색 (BM25 + Vector + RRF + Rerank)
 * 4) 컨텍스트 구성  (dedup / trim / citation keys / 프롬프트 인젝션 방어)
 * 5) ChatClient 호출 (OpenAI 등)
 * 6) 출력 가드레일  (환각 감지, 문서 미근거 답변 차단/경고)
 * 7) 출처(citations) 포함 응답 반환
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

    /**
     * @param query    사용자 질의
     * @param filters  메타데이터 필터 (tenantId, domain 등)
     * @return 답변 + 출처
     */
    public RagAnswerResponse answer(String query, Map<String, Object> filters) {
        return answer(query, filters, false);
    }

    /**
     * @param query         사용자 질의
     * @param filters       메타데이터 필터 (tenantId, domain 등)
     * @param sortByLatest  true면 RRF 이후 createdAt 내림차순 재정렬
     * @return 답변 + 출처
     */
    public RagAnswerResponse answer(String query, Map<String, Object> filters, boolean sortByLatest) {
        String requestId = UUID.randomUUID().toString();

        // 1) 입력 가드레일: 질의가 파이프라인에 진입하기 전 검사
        GuardrailResult inputCheck = inputGuardrailPort.check(query);
        if (inputCheck.isBlocked()) {
            log.warn("[{}] 입력 가드레일 차단: {}", requestId, inputCheck.reason());
            return new RagAnswerResponse(requestId, inputCheck.userMessage(), List.of());
        }
        if (inputCheck.isWarned()) {
            log.warn("[{}] 입력 가드레일 경고: {}", requestId, inputCheck.reason());
        }

        // 2) 쿼리 전처리: BM25용 keywordQuery + Vector용 vectorQuery 생성
        ProcessedQuery processed = queryPreprocessPort.preprocess(query);
        log.info("[{}] 쿼리 전처리 완료: keyword={}, vectorLen={}",
                requestId, processed.keywordQuery(), processed.vectorQuery().length());

        // 3) 하이브리드 검색
        HybridSearchRequest searchReq = HybridSearchRequest.builder()
                .query(query)
                .keywordQuery(processed.keywordQuery())
                .vectorQuery(processed.vectorQuery())
                .topNKeyword(ragProperties.topNKeyword())
                .topNVector(ragProperties.topNVector())
                .topKFinal(ragProperties.topKFinal())
                .vectorThreshold(ragProperties.vectorThreshold())
                .filters(filters)
                .sortByLatest(sortByLatest)
                .build();
        List<SearchHit> ranked = hybridSearchService.search(searchReq, requestId);

        // 4) 문서가 없을 때 fallback (출력 가드레일 미적용 — 비교할 컨텍스트 없음)
        if (ranked.isEmpty()) {
            log.warn("[{}] 검색 결과 없음 → 일반 LLM 답변", requestId);
            return answerWithoutContext(requestId, query);
        }

        // 5) 컨텍스트 구성
        ContextBuilder.BuiltContext built = contextBuilder.build(
                ranked,
                ragProperties.topKFinal(),
                ragProperties.maxCharsPerChunk()
        );

        // 6) LLM 호출
        String answer;
        try {
            answer = chatClient.prompt()
                    .system(prompts.system())
                    .user(prompts.userTemplate().formatted(query, built.contextText()))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("[{}] LLM 호출 실패: {}", requestId, e.getMessage());
            throw new LlmException(RagExceptionEnum.LLM_CALL_FAILED, e);
        }

        // 7) 출력 가드레일: LLM 답변이 문서에 근거하는지 검사
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

    /** 검색 결과가 없을 때 일반 LLM 답변 (RAG 없음, 출력 가드레일 미적용) */
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
}
