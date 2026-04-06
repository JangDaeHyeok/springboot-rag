package com.jdh.rag.adapter;

import com.jdh.rag.port.FeedbackPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 인메모리 모드용 피드백 어댑터 (No-Op).
 * 메모리 모드에서는 search_logs 가 DB에 저장되지 않으므로 피드백 저장도 생략한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rag.keyword-search-type", havingValue = "memory", matchIfMissing = true)
public class NoOpFeedbackAdapter implements FeedbackPort {

    @Override
    public boolean existsByRequestId(String requestId) {
        return true;    // 인메모리 모드에서는 검증 없이 통과
    }

    @Override
    public void updateFeedback(String requestId, boolean accepted) {
        log.debug("피드백 저장 생략 (NoOp): requestId={}, accepted={}", requestId, accepted);
    }
}
