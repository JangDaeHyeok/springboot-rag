package com.jdh.rag.port;

import com.jdh.rag.domain.GuardrailResult;

/**
 * 입력 가드레일 포트.
 *
 * <p>사용자 질의가 검색 파이프라인에 진입하기 전에 호출된다.
 * 구현체: {@code OpenAiGuardrailAdapter}(활성), {@code NoOpGuardrailAdapter}(비활성)
 */
public interface InputGuardrailPort {

    /**
     * 사용자 질의를 검사한다.
     *
     * @param query 사용자 입력 질의
     * @return 판단 결과 ({@link GuardrailResult})
     */
    GuardrailResult check(String query);
}