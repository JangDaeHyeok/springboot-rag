package com.jdh.rag.port;

import com.jdh.rag.domain.GuardrailResult;

/**
 * 입력 가드레일 포트. 검색 파이프라인 진입 전 호출.
 * 구현체: {@code OpenAiGuardrailAdapter}(활성) / {@code NoOpGuardrailAdapter}(비활성)
 */
public interface InputGuardrailPort {

    GuardrailResult check(String query);
}