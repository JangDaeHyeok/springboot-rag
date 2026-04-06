package com.jdh.rag.adapter;

import com.jdh.rag.port.FeedbackPort;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL 기반 사용자 피드백 어댑터.
 * search_logs 테이블의 answer_accepted 컬럼을 업데이트한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.keyword-search-type", havingValue = "postgres")
public class PgFeedbackAdapter implements FeedbackPort {

    private final SearchLogJpaRepository searchLogJpaRepository;

    @Override
    public boolean existsByRequestId(String requestId) {
        return searchLogJpaRepository.existsByRequestId(requestId);
    }

    @Override
    @Transactional
    public void updateFeedback(String requestId, boolean accepted) {
        searchLogJpaRepository.updateAnswerAccepted(requestId, accepted);
        log.debug("피드백 저장: requestId={}, accepted={}", requestId, accepted);
    }
}