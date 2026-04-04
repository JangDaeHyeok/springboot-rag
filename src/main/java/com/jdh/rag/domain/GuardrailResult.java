package com.jdh.rag.domain;

/**
 * 가드레일 판단 결과.
 *
 * <ul>
 *   <li>{@link Status#PASS}  — 정상. 파이프라인 계속 진행.</li>
 *   <li>{@link Status#WARN}  — 주의. 진행은 허용하되 사용자에게 경고 문구를 덧붙인다.</li>
 *   <li>{@link Status#BLOCK} — 차단. 즉시 {@code userMessage}를 응답으로 반환한다.</li>
 * </ul>
 *
 * @param status      판단 결과
 * @param reason      판단 이유 (로깅·디버깅용, LLM 반환값)
 * @param userMessage 사용자에게 노출할 메시지 (PASS 는 null)
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
