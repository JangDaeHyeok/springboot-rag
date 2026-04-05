package com.jdh.rag.domain;

/**
 * 가드레일 판단 결과. PASS(정상) / WARN(진행 허용 + 경고 문구 추가) / BLOCK(즉시 차단).
 *
 * @param reason      LLM 반환 판단 이유 (로깅·디버깅용)
 * @param userMessage 사용자 노출 메시지 (PASS 는 null)
 */
public record GuardrailResult(
        Status status,
        String reason,
        String userMessage
) {

    public enum Status { PASS, WARN, BLOCK }

    // ── 팩토리 ──────────────────────────────────────────────────────────────

    public static GuardrailResult pass() {
        return new GuardrailResult(Status.PASS, "통과", null);
    }

    public static GuardrailResult warn(String reason, String userMessage) {
        return new GuardrailResult(Status.WARN, reason, userMessage);
    }

    public static GuardrailResult block(String reason, String userMessage) {
        return new GuardrailResult(Status.BLOCK, reason, userMessage);
    }

    // ── 편의 메서드 ──────────────────────────────────────────────────────────

    public boolean isBlocked() { return status == Status.BLOCK; }
    public boolean isWarned()  { return status == Status.WARN; }
}
