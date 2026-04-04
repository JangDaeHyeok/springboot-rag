package com.jdh.rag.exception;

import com.jdh.rag.exception.common.RagException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;

/**
 * LLM·임베딩 호출 예외.
 * <ul>
 *   <li>ChatClient(OpenAI 등) 응답 실패 → {@link RagExceptionEnum#LLM_CALL_FAILED}</li>
 *   <li>임베딩 API 실패 → {@link RagExceptionEnum#EMBEDDING_FAILED}</li>
 * </ul>
 */
public class LlmException extends RagException {

    public LlmException(RagExceptionEnum exceptionEnum) {
        super(exceptionEnum);
    }

    public LlmException(RagExceptionEnum exceptionEnum, String message) {
        super(exceptionEnum, message);
    }

    public LlmException(RagExceptionEnum exceptionEnum, Throwable cause) {
        super(exceptionEnum, cause);
    }

    public LlmException(RagExceptionEnum exceptionEnum, String message, Throwable cause) {
        super(exceptionEnum, message, cause);
    }
}