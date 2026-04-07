package com.jdh.rag.service;

import com.jdh.rag.exception.common.RagException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;
import com.jdh.rag.port.FeedbackPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock  private FeedbackPort    feedbackPort;
    @InjectMocks private FeedbackService feedbackService;

    // ── 정상 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("accepted=true이면 포트에 true로 업데이트한다")
    void accepted_true이면_포트에_true로_업데이트한다() {
        // given
        when(feedbackPort.existsByRequestId("req-001")).thenReturn(true);

        // when
        feedbackService.submitFeedback("req-001", true);

        // then
        verify(feedbackPort).updateFeedback("req-001", true);
    }

    @Test
    @DisplayName("accepted=false이면 포트에 false로 업데이트한다")
    void accepted_false이면_포트에_false로_업데이트한다() {
        // given
        when(feedbackPort.existsByRequestId("req-001")).thenReturn(true);

        // when
        feedbackService.submitFeedback("req-001", false);

        // then
        verify(feedbackPort).updateFeedback("req-001", false);
    }

    // ── 예외 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 requestId이면 NOT_FOUND 예외를 던진다")
    void 존재하지_않는_requestId이면_NOT_FOUND_예외를_던진다() {
        // given
        when(feedbackPort.existsByRequestId("req-없음")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> feedbackService.submitFeedback("req-없음", true))
                .isInstanceOf(RagException.class)
                .extracting(e -> ((RagException) e).getExceptionEnum())
                .isEqualTo(RagExceptionEnum.NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않으면 updateFeedback을 호출하지 않는다")
    void 존재하지_않으면_updateFeedback을_호출하지_않는다() {
        // given
        when(feedbackPort.existsByRequestId("req-없음")).thenReturn(false);

        // when
        try { feedbackService.submitFeedback("req-없음", true); } catch (RagException ignored) {}

        // then
        verify(feedbackPort, never()).updateFeedback(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
