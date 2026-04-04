package com.jdh.rag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdh.rag.domain.IngestionRequest;
import com.jdh.rag.domain.IngestionResult;
import com.jdh.rag.exception.IngestionException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;
import com.jdh.rag.service.IngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IngestionController.class)
class IngestionControllerTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean private IngestionService ingestionService;

    // ── 정상 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("텍스트 수집 정상 요청에서 202와 결과를 반환한다")
    void 텍스트_수집_정상_요청에서_202와_결과를_반환한다() throws Exception {
        IngestionResult mockResult = new IngestionResult("law-2024-vehicle-tax", 8);
        when(ingestionService.ingest(any())).thenReturn(mockResult);

        IngestionRequest request = new IngestionRequest(
                "law-2024-vehicle-tax",
                "국세청_차량양도_가이드.pdf",
                "tax",
                "2024.01",
                "default",
                "차량 매각 시 세금 계산 방법에 대한 내용입니다..."
        );

        mockMvc.perform(post("/api/ingest/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.docId").value("law-2024-vehicle-tax"))
                .andExpect(jsonPath("$.chunkCount").value(8));
    }

    @Test
    @DisplayName("파일 업로드 수집 정상 요청에서 202를 반환한다")
    void 파일_업로드_수집_정상_요청에서_202를_반환한다() throws Exception {
        IngestionResult mockResult = new IngestionResult("doc-from-file", 5);
        when(ingestionService.ingestResource(any(), any())).thenReturn(mockResult);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf",
                "fake pdf content".getBytes()
        );

        mockMvc.perform(multipart("/api/ingest/file")
                        .file(file)
                        .param("docId",    "doc-from-file")
                        .param("source",   "test.pdf")
                        .param("domain",   "tax")
                        .param("version",  "2024.01")
                        .param("tenantId", "default"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.docId").value("doc-from-file"))
                .andExpect(jsonPath("$.chunkCount").value(5));
    }

    @Test
    @DisplayName("tenantId 기본값은 default이다")
    void tenantId_기본값은_default이다() throws Exception {
        IngestionResult mockResult = new IngestionResult("doc-001", 3);
        when(ingestionService.ingestResource(any(), any())).thenReturn(mockResult);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "content".getBytes()
        );

        mockMvc.perform(multipart("/api/ingest/file")
                        .file(file)
                        .param("docId",   "doc-001")
                        .param("source",  "test.txt")
                        .param("domain",  "faq")
                        .param("version", "1.0"))
                .andExpect(status().isAccepted());
    }

    // ── 400 Bad Request 검증 ───────────────────────────────────────────────────

    @Test
    @DisplayName("빈 content이면 400과 errorCode I0003을 반환한다")
    void 빈_content이면_400과_errorCode_I0003을_반환한다() throws Exception {
        IngestionRequest request = new IngestionRequest(
                "doc-001", "source.txt", "faq", "1.0", "default", ""
        );

        mockMvc.perform(post("/api/ingest/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("I0003"))
                .andExpect(jsonPath("$.errorMsg").isNotEmpty());
    }

    @Test
    @DisplayName("빈 파일 업로드이면 400과 errorCode I0003을 반환한다")
    void 빈_파일_업로드이면_400과_errorCode_I0003을_반환한다() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]
        );

        mockMvc.perform(multipart("/api/ingest/file")
                        .file(emptyFile)
                        .param("docId",   "doc-001")
                        .param("source",  "empty.pdf")
                        .param("domain",  "tax")
                        .param("version", "1.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("I0003"))
                .andExpect(jsonPath("$.errorMsg").isNotEmpty());
    }

    // ── 서비스 예외 전파 검증 ──────────────────────────────────────────────────

    @Test
    @DisplayName("서비스에서 FILE_PARSE_FAILED 발생 시 422와 errorCode I0002를 반환한다")
    void 서비스_FILE_PARSE_FAILED_422_I0002() throws Exception {
        when(ingestionService.ingestResource(any(), any()))
                .thenThrow(new IngestionException(RagExceptionEnum.FILE_PARSE_FAILED,
                        "파일 파싱에 실패하였습니다: test.pdf"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "corrupt".getBytes()
        );

        mockMvc.perform(multipart("/api/ingest/file")
                        .file(file)
                        .param("docId",   "doc-001")
                        .param("source",  "test.pdf")
                        .param("domain",  "tax")
                        .param("version", "1.0"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("I0002"))
                .andExpect(jsonPath("$.errorMsg").value("파일 파싱에 실패하였습니다: test.pdf"));
    }

    @Test
    @DisplayName("서비스에서 VECTOR_STORE_FAILED 발생 시 503과 errorCode I0004를 반환한다")
    void 서비스_VECTOR_STORE_FAILED_503_I0004() throws Exception {
        when(ingestionService.ingest(any()))
                .thenThrow(new IngestionException(RagExceptionEnum.VECTOR_STORE_FAILED));

        IngestionRequest request = new IngestionRequest(
                "doc-001", "source.txt", "faq", "1.0", "default", "콘텐츠"
        );

        mockMvc.perform(post("/api/ingest/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("I0004"));
    }

    @Test
    @DisplayName("서비스에서 INGESTION_FAILED 발생 시 500과 errorCode I0001을 반환한다")
    void 서비스_INGESTION_FAILED_500_I0001() throws Exception {
        when(ingestionService.ingest(any()))
                .thenThrow(new IngestionException(RagExceptionEnum.INGESTION_FAILED));

        IngestionRequest request = new IngestionRequest(
                "doc-001", "source.txt", "faq", "1.0", "default", "콘텐츠"
        );

        mockMvc.perform(post("/api/ingest/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("I0001"));
    }
}