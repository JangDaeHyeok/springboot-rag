package com.jdh.rag.port;

import com.jdh.rag.domain.GuardrailResult;

/**
 * 출력 가드레일 포트.
 *
 * <p>LLM이 답변을 생성한 후, 사용자에게 응답을 반환하기 전에 호출된다.
 * 문서 근거가 불충분하거나 환각(hallucination)이 감지되면 WARN/BLOCK을 반환한다.
 * 구현체: {@code OpenAiGuardrailAdapter}(활성), {@code NoOpGuardrailAdapter}(비활성)
 */
public interface OutputGuardrailPort {

    /**
     * 생성된 답변과 참고 문서를 비교·검증한다.
     *
     * @param answer      LLM이 생성한 답변
     * @param contextText RAG 파이프라인이 주입한 참고 문서 컨텍스트
     * @return 판단 결과 ({@link GuardrailResult})
     */
    GuardrailResult check(String answer, String contextText);
}