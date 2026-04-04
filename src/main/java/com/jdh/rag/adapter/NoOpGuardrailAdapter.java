package com.jdh.rag.adapter;

import com.jdh.rag.domain.GuardrailResult;
import com.jdh.rag.port.InputGuardrailPort;
import com.jdh.rag.port.OutputGuardrailPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 가드레일 비활성 어댑터 (No-Op).
 *
 * <p>{@code rag.guardrail.enabled=false} 이거나 설정이 없을 때 사용된다.
 * 모든 검사를 즉시 PASS로 통과시킨다.
 */
@Component
@ConditionalOnProperty(name = "rag.guardrail.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpGuardrailAdapter implements InputGuardrailPort, OutputGuardrailPort {

    @Override
    public GuardrailResult check(String query) {
        return GuardrailResult.pass();
    }

    @Override
    public GuardrailResult check(String answer, String contextText) {
        return GuardrailResult.pass();
    }
}
