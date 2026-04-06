package com.jdh.rag.exception;

import com.jdh.rag.adapter.DouzoneEmbeddingModel;
import com.jdh.rag.exception.LlmException;
import com.jdh.rag.exception.common.RagException;
import com.jdh.rag.exception.common.RagExceptionAdvice;
import com.jdh.rag.exception.common.RagExceptionEntity;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RagExceptionAdvice} 단위 테스트.
 * Spring 컨텍스트 없이 advice 메서드를 직접 호출해 검증한다.
 */
class RagExceptionAdviceTest {

    private final RagExceptionAdvice advice = new RagExceptionAdvice();
    private HttpServletRequest req;

    @BeforeEach
    void setUp() {
        req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/test");
    }

    // ── IngestionException ────────────────────────────────────────────────────

    @Test
    @DisplayName("EMPTY_CONTENT → 400 + I0003")
    void emptyContent_400_I0003() {
        IngestionException e = new IngestionException(RagExceptionEnum.EMPTY_CONTENT);
        ResponseEntity<RagExceptionEntity> res = advice.handleIngestionException(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().errorCode()).isEqualTo("I0003");
        assertThat(res.getBody().errorMsg()).isEqualTo(RagExceptionEnum.EMPTY_CONTENT.getMessage());
    }

    @Test
    @DisplayName("FILE_PARSE_FAILED → 422 + I0002")
    void fileParseFailed_422_I0002() {
        IngestionException e = new IngestionException(RagExceptionEnum.FILE_PARSE_FAILED, "파싱 실패: test.pdf");
        ResponseEntity<RagExceptionEntity> res = advice.handleIngestionException(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().errorCode()).isEqualTo("I0002");
        assertThat(res.getBody().errorMsg()).isEqualTo("파싱 실패: test.pdf");
    }

    @Test
    @DisplayName("VECTOR_STORE_FAILED → 503 + I0004")
    void vectorStoreFailed_503_I0004() {
        IngestionException e = new IngestionException(RagExceptionEnum.VECTOR_STORE_FAILED);
        ResponseEntity<RagExceptionEntity> res = advice.handleIngestionException(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(res.getBody().errorCode()).isEqualTo("I0004");
    }

    @Test
    @DisplayName("INGESTION_FAILED → 500 + I0001")
    void ingestionFailed_500_I0001() {
        IngestionException e = new IngestionException(RagExceptionEnum.INGESTION_FAILED);
        ResponseEntity<RagExceptionEntity> res = advice.handleIngestionException(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().errorCode()).isEqualTo("I0001");
    }

    @Test
    @DisplayName("커스텀 메시지가 응답 errorMsg에 포함된다")
    void customMessage_isPreserved() {
        String customMsg = "docId=law-2024-vehicle-tax 파싱 실패";
        IngestionException e = new IngestionException(RagExceptionEnum.FILE_PARSE_FAILED, customMsg);
        ResponseEntity<RagExceptionEntity> res = advice.handleIngestionException(req, e);

        assertThat(res.getBody().errorMsg()).isEqualTo(customMsg);
    }

    // ── SearchException ───────────────────────────────────────────────────────

    @Test
    @DisplayName("SearchException(SEARCH_UNAVAILABLE) → 503 + S0001")
    void searchUnavailable_503_S0001() {
        SearchException e = new SearchException(RagExceptionEnum.SEARCH_UNAVAILABLE);
        ResponseEntity<RagExceptionEntity> res = advice.handleSearchException(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(res.getBody().errorCode()).isEqualTo("S0001");
        assertThat(res.getBody().errorMsg()).isEqualTo(RagExceptionEnum.SEARCH_UNAVAILABLE.getMessage());
    }

    // ── LlmException ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("LlmException(LLM_CALL_FAILED) → 503 + L0001")
    void llmCallFailed_503_L0001() {
        LlmException e = new LlmException(RagExceptionEnum.LLM_CALL_FAILED);
        ResponseEntity<RagExceptionEntity> res = advice.handleLlmException(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(res.getBody().errorCode()).isEqualTo("L0001");
    }

    @Test
    @DisplayName("LlmException(EMBEDDING_FAILED) → 503 + L0002")
    void embeddingFailed_503_L0002() {
        LlmException e = new LlmException(RagExceptionEnum.EMBEDDING_FAILED);
        ResponseEntity<RagExceptionEntity> res = advice.handleLlmException(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(res.getBody().errorCode()).isEqualTo("L0002");
    }

    // ── RagException (base) ───────────────────────────────────────────────────

    @Test
    @DisplayName("RagException(BAD_REQUEST) → 400 + R0002")
    void ragBadRequest_400_R0002() {
        RagException e = new RagException(RagExceptionEnum.BAD_REQUEST, "질의(query)는 필수입니다.");
        ResponseEntity<RagExceptionEntity> res = advice.handleRagException(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().errorCode()).isEqualTo("R0002");
        assertThat(res.getBody().errorMsg()).isEqualTo("질의(query)는 필수입니다.");
    }

    @Test
    @DisplayName("RagException(DOCUMENT_NOT_FOUND) → 404 + D0001")
    void documentNotFound_404_D0001() {
        RagException e = new RagException(RagExceptionEnum.DOCUMENT_NOT_FOUND, "docId=doc-001");
        ResponseEntity<RagExceptionEntity> res = advice.handleRagException(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody().errorCode()).isEqualTo("D0001");
        assertThat(res.getBody().errorMsg()).isEqualTo("docId=doc-001");
    }

    // ── DouzoneEmbeddingException ─────────────────────────────────────────────
    // DouzoneEmbeddingException은 LlmException을 상속하므로 handleLlmException에서 처리된다.

    @Test
    @DisplayName("DouzoneEmbeddingException → LlmException 핸들러 → 503 + L0002")
    void douzoneEmbedding_503_L0002() {
        DouzoneEmbeddingModel.DouzoneEmbeddingException e =
                new DouzoneEmbeddingModel.DouzoneEmbeddingException("Douzone 임베딩 API 오류: HTTP 500");

        // DouzoneEmbeddingException은 LlmException 하위 타입
        assertThat(e).isInstanceOf(LlmException.class);
        assertThat(e.getExceptionEnum()).isEqualTo(RagExceptionEnum.EMBEDDING_FAILED);

        ResponseEntity<RagExceptionEntity> res = advice.handleLlmException(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(res.getBody().errorCode()).isEqualTo("L0002");
    }

    // ── Spring MVC 표준 예외 ───────────────────────────────────────────────────

    @Test
    @DisplayName("NoHandlerFoundException → 404 + R0003")
    void noHandlerFound_404_R0003() throws Exception {
        NoHandlerFoundException e = new NoHandlerFoundException("GET", "/unknown-path", null);
        ResponseEntity<RagExceptionEntity> res = advice.handleNoHandlerFound(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody().errorCode()).isEqualTo("R0003");
        assertThat(res.getBody().errorMsg()).isEqualTo(RagExceptionEnum.NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException → 405 + R0004")
    void methodNotSupported_405_R0004() {
        HttpRequestMethodNotSupportedException e =
                new HttpRequestMethodNotSupportedException("DELETE");
        ResponseEntity<RagExceptionEntity> res = advice.handleMethodNotSupported(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(res.getBody().errorCode()).isEqualTo("R0004");
    }

    @Test
    @DisplayName("MissingServletRequestParameterException → 400 + R0002, 파라미터명 포함")
    void missingParam_400_R0002() {
        MissingServletRequestParameterException e =
                new MissingServletRequestParameterException("docId", "String");
        ResponseEntity<RagExceptionEntity> res = advice.handleMissingParam(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().errorCode()).isEqualTo("R0002");
        assertThat(res.getBody().errorMsg()).contains("docId");
    }

    @Test
    @DisplayName("IllegalArgumentException → 400 + R0002")
    void illegalArgument_400_R0002() {
        IllegalArgumentException e = new IllegalArgumentException("잘못된 파라미터");
        ResponseEntity<RagExceptionEntity> res = advice.handleIllegalArgument(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().errorCode()).isEqualTo("R0002");
    }

    // ── Multipart 예외 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("MaxUploadSizeExceededException → 400 + R0002")
    void maxUploadSize_400_R0002() {
        MaxUploadSizeExceededException e = new MaxUploadSizeExceededException(10_485_760L);
        ResponseEntity<RagExceptionEntity> res = advice.handleMaxUploadSizeExceeded(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().errorCode()).isEqualTo("R0002");
        assertThat(res.getBody().errorMsg()).contains("업로드");
    }

    // ── 폴백 예외 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RuntimeException → 500 + R0001")
    void runtimeException_500_R0001() {
        RuntimeException e = new RuntimeException("예상치 못한 오류");
        ResponseEntity<RagExceptionEntity> res = advice.handleRuntimeException(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().errorCode()).isEqualTo("R0001");
        assertThat(res.getBody().errorMsg()).isEqualTo(RagExceptionEnum.RUNTIME_EXCEPTION.getMessage());
    }

    @Test
    @DisplayName("Exception → 500 + R9999")
    void exception_500_R9999() {
        Exception e = new Exception("기타 오류");
        ResponseEntity<RagExceptionEntity> res = advice.handleException(req, e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().errorCode()).isEqualTo("R9999");
    }
}