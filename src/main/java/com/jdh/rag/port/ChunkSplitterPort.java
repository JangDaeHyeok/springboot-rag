package com.jdh.rag.port;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 문서 청킹 포트.
 * 입력 문서 목록을 청크 단위로 분리한다.
 *
 * <p>구현체 선택: {@code rag.chunk.strategy}
 * <ul>
 *   <li>{@code fixed}  (기본값) : 고정 토큰 크기 기반 청킹 ({@link com.jdh.rag.adapter.chunk.FixedTokenChunkSplitter})</li>
 *   <li>{@code semantic}: 구조 감지 + OpenAI 기반 시맨틱 청킹 ({@link com.jdh.rag.adapter.chunk.SemanticChunkSplitter})</li>
 * </ul>
 */
public interface ChunkSplitterPort {

    List<Document> split(List<Document> docs);
}
