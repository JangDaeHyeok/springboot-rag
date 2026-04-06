package com.jdh.rag.controller;

import com.jdh.rag.domain.RagAnswerRequest;
import com.jdh.rag.domain.RagAnswerResponse;
import com.jdh.rag.exception.LlmException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;
import com.jdh.rag.service.FeedbackService;
import com.jdh.rag.service.RagAnswerService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagController.class)
class RagControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private RagAnswerService ragAnswerService;
    @MockitoBean private FeedbackService  feedbackService;

    // ── 정상 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 요청에서 200과 RAG 답변을 반환한다")
    void 정상_요청에서_200과_RAG_답변을_반환한다() throws Exception {
        RagAnswerResponse mockResponse = new RagAnswerResponse(
                "req-001",
                "세금 계산 방법은 [S1]에 따라 ...",
                List.of(RagAnswerResponse.Citation.builder()
                        .citeKey("S1").docId("doc-A").chunkId("chunk-1")
                        .source("source.pdf").snippet("snippet...").meta(Map.of())
                        .build())
        );
        when(ragAnswerService.answer(any(RagAnswerRequest.class))).thenReturn(mockResponse);

        RagController.AnswerRequest request = new RagController.AnswerRequest(
                "세금 계산", "default", "tax", null
        );

        mockMvc.perform(post("/api/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-001"))
                .andExpect(jsonPath("$.answer").value("세금 계산 방법은 [S1]에 따라 ..."))
                .andExpect(jsonPath("$.citations").isArray())
                .andExpect(jsonPath("$.citations[0].citeKey").value("S1"))
                .andExpect(jsonPath("$.citations[0].source").value("source.pdf"));
    }

    @Test
    @DisplayName("tenantId, domain 없어도 요청이 처리된다")
    void tenantId_domain_없어도_요청이_처리된다() throws Exception {
        when(ragAnswerService.answer(any(RagAnswerRequest.class)))
                .thenReturn(new RagAnswerResponse("req-002", "답변", List.of()));

        RagController.AnswerRequest request = new RagController.AnswerRequest(
                "질의", null, null, null
        );

        mockMvc.perform(post("/api/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations").isArray());
    }

    @Test
    @DisplayName("검색 결과 없어도 200으로 빈 citations 반환한다")
    void 검색_결과_없어도_200으로_빈_citations_반환한다() throws Exception {
        when(ragAnswerService.answer(any(RagAnswerRequest.class)))
                .thenReturn(new RagAnswerResponse("req-003", "문서에서 확인되지 않습니다.", List.of()));

        mockMvc.perform(post("/api/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RagController.AnswerRequest("알 수 없는 질의", "default", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations").isEmpty());
    }

    // ── 400 Bad Request 검증 ───────────────────────────────────────────────────

    @Test
    @DisplayName("빈 query이면 400과 errorCode R0002를 반환한다")
    void 빈_query이면_400과_errorCode_R0002를_반환한다() throws Exception {
        mockMvc.perform(post("/api/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RagController.AnswerRequest("", "default", "tax", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("R0002"))
                .andExpect(jsonPath("$.errorMsg").isNotEmpty());
    }

    @Test
    @DisplayName("null query이면 400과 errorCode R0002를 반환한다")
    void null_query이면_400과_errorCode_R0002를_반환한다() throws Exception {
        mockMvc.perform(post("/api/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RagController.AnswerRequest(null, "default", "tax", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("R0002"));
    }

    // ── 서비스 예외 전파 검증 ──────────────────────────────────────────────────

    @Test
    @DisplayName("서비스에서 LlmException 발생 시 503과 errorCode L0001을 반환한다")
    void 서비스_LlmException_503_L0001() throws Exception {
        when(ragAnswerService.answer(any(RagAnswerRequest.class)))
                .thenThrow(new LlmException(RagExceptionEnum.LLM_CALL_FAILED));

        mockMvc.perform(post("/api/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RagController.AnswerRequest("세금 계산", "default", "tax", null))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("L0001"))
                .andExpect(jsonPath("$.errorMsg").isNotEmpty());
    }

    @Test
    @DisplayName("서비스에서 LlmException 발생 시 errorMsg가 응답에 포함된다")
    void 서비스_LlmException_errorMsg_포함() throws Exception {
        when(ragAnswerService.answer(any(RagAnswerRequest.class)))
                .thenThrow(new LlmException(RagExceptionEnum.LLM_CALL_FAILED, "OpenAI API timeout"));

        mockMvc.perform(post("/api/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RagController.AnswerRequest("질의", null, null, null))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("L0001"))
                .andExpect(jsonPath("$.errorMsg").value("OpenAI API timeout"));
    }

    @Test
    @DisplayName("서비스에서 RuntimeException 발생 시 500과 errorCode R0001을 반환한다")
    void 서비스_RuntimeException_500_R0001() throws Exception {
        when(ragAnswerService.answer(any(RagAnswerRequest.class)))
                .thenThrow(new RuntimeException("예상치 못한 서버 오류"));

        mockMvc.perform(post("/api/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RagController.AnswerRequest("질의", null, null, null))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("R0001"));
    }

    // ── 피드백 API ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("피드백 accepted=true이면 204를 반환한다")
    void 피드백_accepted_true이면_204를_반환한다() throws Exception {
        mockMvc.perform(patch("/api/rag/feedback/req-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\": true}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("피드백 accepted=false이면 204를 반환한다")
    void 피드백_accepted_false이면_204를_반환한다() throws Exception {
        mockMvc.perform(patch("/api/rag/feedback/req-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\": false}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("피드백 accepted 필드 없으면 400을 반환한다")
    void 피드백_accepted_필드_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/rag/feedback/req-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("R0002"));
    }
}
