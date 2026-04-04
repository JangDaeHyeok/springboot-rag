package com.jdh.rag.exception;

import com.jdh.rag.exception.common.RagException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;

/**
 * 문서 수집(Ingestion) 파이프라인 예외.
 * <ul>
 *   <li>빈 콘텐츠 → {@link RagExceptionEnum#EMPTY_CONTENT}</li>
 *   <li>파일 파싱 실패 → {@link RagExceptionEnum#FILE_PARSE_FAILED}</li>
 *   <li>벡터 저장소 저장 실패 → {@link RagExceptionEnum#VECTOR_STORE_FAILED}</li>
 *   <li>기타 수집 실패 → {@link RagExceptionEnum#INGESTION_FAILED}</li>
 * </ul>
 */
public class IngestionException extends RagException {

    public IngestionException(RagExceptionEnum exceptionEnum) {
        super(exceptionEnum);
    }

    public IngestionException(RagExceptionEnum exceptionEnum, String message) {
        super(exceptionEnum, message);
    }

    public IngestionException(RagExceptionEnum exceptionEnum, Throwable cause) {
        super(exceptionEnum, cause);
    }

    public IngestionException(RagExceptionEnum exceptionEnum, String message, Throwable cause) {
        super(exceptionEnum, message, cause);
    }
}
