package com.jdh.rag.port;

/**
 * 사용자 피드백 포트.
 * requestId 기준으로 search_logs 의 answer_accepted 컬럼을 업데이트한다.
 */
public interface FeedbackPort {

    /** requestId에 해당하는 검색 로그가 존재하는지 확인한다. */
    boolean existsByRequestId(String requestId);

    /** requestId에 해당하는 모든 검색 로그의 answer_accepted를 업데이트한다. */
    void updateFeedback(String requestId, boolean accepted);
}
