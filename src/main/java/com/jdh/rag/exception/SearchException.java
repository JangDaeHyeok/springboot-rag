package com.jdh.rag.exception;

import com.jdh.rag.exception.common.RagException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;

/**
 * 하이브리드 검색 파이프라인 예외.
 * 키워드 · 벡터 채널이 모두 이용 불가한 경우에 사용한다.
 * → {@link RagExceptionEnum#SEARCH_UNAVAILABLE}
 */
public class SearchException extends RagException {

    public SearchException(RagExceptionEnum exceptionEnum) {
        super(exceptionEnum);
    }

    public SearchException(RagExceptionEnum exceptionEnum, String message) {
        super(exceptionEnum, message);
    }

    public SearchException(RagExceptionEnum exceptionEnum, Throwable cause) {
        super(exceptionEnum, cause);
    }
}