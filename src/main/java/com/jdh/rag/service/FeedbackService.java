package com.jdh.rag.service;

import com.jdh.rag.exception.common.RagException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;
import com.jdh.rag.port.FeedbackPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 사용자 피드백 처리 서비스.
 * requestId에 해당하는 검색 로그의 answer_accepted 를 업데이트해 RAG 품질 개선 루프를 구성한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackPort feedbackPort;

    public void submitFeedback(String requestId, boolean accepted) {
        if (!feedbackPort.existsByRequestId(requestId)) {
            throw new RagException(RagExceptionEnum.NOT_FOUND,
                    "requestId=" + requestId + "에 해당하는 검색 로그가 없습니다.");
        }
        feedbackPort.updateFeedback(requestId, accepted);
    }
}
