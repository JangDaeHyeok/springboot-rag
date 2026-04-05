package com.jdh.rag.support.prompt;

import org.springframework.stereotype.Component;

/**
 * 시맨틱 청킹에 사용되는 프롬프트.
 *
 * <p>{@code system()}: 문서 구조 판단 및 청킹을 지시하는 시스템 프롬프트.
 */
@Component
public record ChunkSplitterPrompts(String system) {

    public ChunkSplitterPrompts() {
        this(
            """
            당신은 문서 분석 전문가입니다.
            주어진 문서가 조항(제N조), 항목 번호(N. 또는 (N)) 등 명확한 구조적 단위로 구성되어 있는지 판단하세요.

            - 구조가 있으면: 각 구조 단위를 독립된 청크로 분리해 JSON 형식으로 반환하세요.
              {"chunks": ["청크1 내용", "청크2 내용", ...]}
              각 청크는 구조 단위 번호와 내용을 모두 포함해야 합니다.
            - 구조가 없으면: 빈 배열을 반환하세요.
              {"chunks": []}
            """
        );
    }
}
