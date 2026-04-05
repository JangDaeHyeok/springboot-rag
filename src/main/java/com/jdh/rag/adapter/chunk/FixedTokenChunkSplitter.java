package com.jdh.rag.adapter.chunk;

import com.jdh.rag.config.RagProperties;
import com.jdh.rag.port.ChunkSplitterPort;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 고정 토큰 크기 기반 청킹 어댑터.
 *
 * <p>{@code rag.chunk.strategy=fixed} (기본값) 일 때 활성화된다.
 * Spring AI {@link TokenTextSplitter}로 문서를 고정 크기로 분리한다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.chunk.strategy", havingValue = "fixed")
public class FixedTokenChunkSplitter implements ChunkSplitterPort {

    private final RagProperties ragProperties;

    @Override
    public List<Document> split(List<Document> docs) {
        return TokenTextSplitter.builder()
                .withChunkSize(ragProperties.chunk().size())
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build()
                .apply(docs);
    }
}