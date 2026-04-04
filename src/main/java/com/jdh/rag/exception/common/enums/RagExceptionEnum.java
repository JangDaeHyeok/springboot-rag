package com.jdh.rag.exception.common.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum RagExceptionEnum {

    // ── 공통 (R) ──────────────────────────────────────────────────────────────
    RUNTIME_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "R0001", "서버 오류")
    , BAD_REQUEST(HttpStatus.BAD_REQUEST,             "R0002", "잘못된 요청입니다.")
    , NOT_FOUND(HttpStatus.NOT_FOUND,                 "R0003", "존재하지 않는 리소스입니다.")
    , METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "R0004", "요청 메소드를 확인해주세요.")
    , INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "R9999", "기타 오류")

    // ── Ingestion (I) ─────────────────────────────────────────────────────────
    , INGESTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "I0001", "문서 수집에 실패하였습니다.")
    , FILE_PARSE_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "I0002", "파일 파싱에 실패하였습니다.")
    , EMPTY_CONTENT(HttpStatus.BAD_REQUEST,              "I0003", "수집할 문서 내용이 없습니다.")
    , VECTOR_STORE_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "I0004", "벡터 저장소 저장에 실패하였습니다.")

    // ── Search (S) ────────────────────────────────────────────────────────────
    , SEARCH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "S0001", "검색 서비스를 사용할 수 없습니다.")

    // ── LLM / Embedding (L) ───────────────────────────────────────────────────
    , LLM_CALL_FAILED(HttpStatus.SERVICE_UNAVAILABLE,  "L0001", "AI 답변 생성에 실패하였습니다.")
    , EMBEDDING_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "L0002", "임베딩 처리에 실패하였습니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;

    RagExceptionEnum(HttpStatus status, String code, String message) {
        this.status  = status;
        this.code    = code;
        this.message = message;
    }
}