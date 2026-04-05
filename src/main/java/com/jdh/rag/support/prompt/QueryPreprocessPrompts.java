package com.jdh.rag.support.prompt;

import org.springframework.stereotype.Component;

/**
 * 쿼리 전처리에 사용되는 프롬프트.
 *
 * <p>{@code system()}: keywordQuery / vectorQuery 생성을 지시하는 시스템 프롬프트.
 * <p>{@code userTemplate()}: 원문 쿼리를 담는 사용자 프롬프트 템플릿 ({@code %s}: query).
 */
@Component
public record QueryPreprocessPrompts(String system, String userTemplate) {

    public QueryPreprocessPrompts() {
        this(
            """
            당신은 검색 쿼리 최적화 전문가입니다.
            사용자 질의를 분석하고 반드시 아래 JSON 형식으로만 응답하세요. 다른 설명은 불필요합니다.
            {
              "keywordQuery": "BM25 키워드 검색에 최적화된 핵심 명사·동사 중심 쿼리",
              "vectorQuery": "해당 질문의 이상적인 답변이 담긴 가상의 문서 내용 (2~3문장)"
            }
            """,
            """
            원문 질의: %s

            변환 기준:
            - keywordQuery: 구어체·조사·접속사를 제거하고 핵심 명사와 동사만 남겨 BM25 검색어로 만드세요.
            - vectorQuery: 이 질문에 대한 이상적인 답변이 실제로 문서에 있다면 어떤 내용일지 2~3문장으로 서술하세요. (HyDE 기법)
            """
        );
    }
}
