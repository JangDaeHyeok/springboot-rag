package com.jdh.rag.exception.common;

import lombok.Builder;

@Builder
public record RagExceptionEntity(String errorCode, String errorMsg) {
}