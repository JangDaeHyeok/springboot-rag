package com.jdh.rag.exception.common;

import com.jdh.rag.exception.common.enums.RagExceptionEnum;
import lombok.Getter;

/**
 * RAG 서비스 예외의 공통 기반 클래스.
 * 서비스별 예외는 이 클래스를 상속받아 구체화한다.
 */
@Getter
public class RagException extends RuntimeException {

    private final RagExceptionEnum exceptionEnum;

    public RagException(RagExceptionEnum exceptionEnum) {
        super(exceptionEnum.getMessage());
        this.exceptionEnum = exceptionEnum;
    }

    public RagException(RagExceptionEnum exceptionEnum, String message) {
        super(message);
        this.exceptionEnum = exceptionEnum;
    }

    public RagException(RagExceptionEnum exceptionEnum, Throwable cause) {
        super(exceptionEnum.getMessage(), cause);
        this.exceptionEnum = exceptionEnum;
    }

    public RagException(RagExceptionEnum exceptionEnum, String message, Throwable cause) {
        super(message, cause);
        this.exceptionEnum = exceptionEnum;
    }
}
