package com.jdh.rag.exception.common;

import com.jdh.rag.exception.IngestionException;
import com.jdh.rag.exception.LlmException;
import com.jdh.rag.exception.SearchException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * RAG 서비스 전역 예외 처리.
 * 우선순위: 서비스별(Ingestion/Search/Llm) → 공통 RagException → Spring MVC 표준 → Multipart → 폴백
 */
@RestControllerAdvice
@Slf4j
public class RagExceptionAdvice {

    // ── 서비스별 예외 ──────────────────────────────────────────────────────────

    @ExceptionHandler(IngestionException.class)
    public ResponseEntity<RagExceptionEntity> handleIngestionException(
            HttpServletRequest req, IngestionException e) {
        log.warn("[IngestionException] uri={}, code={}, msg={}",
                req.getRequestURI(), e.getExceptionEnum().getCode(), e.getMessage());
        return buildResponse(e.getExceptionEnum(), e.getMessage());
    }

    @ExceptionHandler(SearchException.class)
    public ResponseEntity<RagExceptionEntity> handleSearchException(
            HttpServletRequest req, SearchException e) {
        log.error("[SearchException] uri={}, code={}, msg={}",
                req.getRequestURI(), e.getExceptionEnum().getCode(), e.getMessage());
        return buildResponse(e.getExceptionEnum(), e.getMessage());
    }

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<RagExceptionEntity> handleLlmException(
            HttpServletRequest req, LlmException e) {
        log.error("[LlmException] uri={}, code={}, msg={}",
                req.getRequestURI(), e.getExceptionEnum().getCode(), e.getMessage());
        return buildResponse(e.getExceptionEnum(), e.getMessage());
    }

    // ── 공통 기반 예외 ─────────────────────────────────────────────────────────

    @ExceptionHandler(RagException.class)
    public ResponseEntity<RagExceptionEntity> handleRagException(
            HttpServletRequest req, RagException e) {
        log.warn("[RagException] uri={}, code={}, msg={}",
                req.getRequestURI(), e.getExceptionEnum().getCode(), e.getMessage());
        return buildResponse(e.getExceptionEnum(), e.getMessage());
    }

    // ── Spring MVC 표준 예외 ───────────────────────────────────────────────────

    /** 404 - 매핑되지 않은 경로 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<RagExceptionEntity> handleNoHandlerFound(
            HttpServletRequest req, NoHandlerFoundException e) {
        log.info("[NoHandlerFoundException] uri={}", req.getRequestURI());
        return buildResponse(RagExceptionEnum.NOT_FOUND, RagExceptionEnum.NOT_FOUND.getMessage());
    }

    /** 405 - 지원하지 않는 HTTP 메소드 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RagExceptionEntity> handleMethodNotSupported(
            HttpServletRequest req, HttpRequestMethodNotSupportedException e) {
        log.info("[MethodNotSupportedException] uri={}, method={}", req.getRequestURI(), e.getMethod());
        return buildResponse(RagExceptionEnum.METHOD_NOT_ALLOWED, RagExceptionEnum.METHOD_NOT_ALLOWED.getMessage());
    }

    /** 400 - 필수 요청 파라미터 누락 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<RagExceptionEntity> handleMissingParam(
            HttpServletRequest req, MissingServletRequestParameterException e) {
        log.info("[MissingServletRequestParameterException] uri={}, param={}", req.getRequestURI(), e.getParameterName());
        String msg = "필수 파라미터가 누락되었습니다: " + e.getParameterName();
        return buildResponse(RagExceptionEnum.BAD_REQUEST, msg);
    }

    /** 400 - IllegalArgumentException */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RagExceptionEntity> handleIllegalArgument(
            HttpServletRequest req, IllegalArgumentException e) {
        log.info("[IllegalArgumentException] uri={}, msg={}", req.getRequestURI(), e.getMessage());
        return buildResponse(RagExceptionEnum.BAD_REQUEST, RagExceptionEnum.BAD_REQUEST.getMessage());
    }

    // ── 파일 업로드 예외 ───────────────────────────────────────────────────────

    /** 413 - 파일 크기 초과 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<RagExceptionEntity> handleMaxUploadSizeExceeded(
            HttpServletRequest req, MaxUploadSizeExceededException e) {
        log.warn("[MaxUploadSizeExceededException] uri={}, msg={}", req.getRequestURI(), e.getMessage());
        return buildResponse(RagExceptionEnum.BAD_REQUEST, "업로드 파일 크기가 허용 범위를 초과하였습니다.");
    }

    /** 400 - Multipart 처리 오류 */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<RagExceptionEntity> handleMultipart(
            HttpServletRequest req, MultipartException e) {
        log.warn("[MultipartException] uri={}, msg={}", req.getRequestURI(), e.getMessage());
        return buildResponse(RagExceptionEnum.BAD_REQUEST, "파일 업로드 요청이 올바르지 않습니다.");
    }

    // ── 폴백 예외 ─────────────────────────────────────────────────────────────

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<RagExceptionEntity> handleRuntimeException(
            HttpServletRequest req, RuntimeException e) {
        log.error("[RuntimeException] uri={}", req.getRequestURI(), e);
        return buildResponse(RagExceptionEnum.RUNTIME_EXCEPTION, RagExceptionEnum.RUNTIME_EXCEPTION.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RagExceptionEntity> handleException(
            HttpServletRequest req, Exception e) {
        log.error("[Exception] uri={}", req.getRequestURI(), e);
        return buildResponse(RagExceptionEnum.INTERNAL_SERVER_ERROR, RagExceptionEnum.INTERNAL_SERVER_ERROR.getMessage());
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private ResponseEntity<RagExceptionEntity> buildResponse(RagExceptionEnum exceptionEnum, String message) {
        return ResponseEntity
                .status(exceptionEnum.getStatus())
                .body(RagExceptionEntity.builder()
                        .errorCode(exceptionEnum.getCode())
                        .errorMsg(message)
                        .build());
    }
}
