package com.jdh.rag.adapter.chunk;

import com.jdh.rag.config.RagProperties;
import com.jdh.rag.port.ChunkSplitterPort;
import com.jdh.rag.support.prompt.ChunkSplitterPrompts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * OpenAI 기반 시맨틱 청킹 어댑터.
 *
 * <p>{@code rag.chunk.strategy=semantic} (기본값) 일 때 활성화된다.
 *
 * <p>구조 판단도 LLM이 수행한다. LLM이 구조가 없다고 판단하면 빈 배열을 반환하며,
 * 이 경우 고정 토큰 크기 방식으로 자동 대체한다.
 *
 * <p>텍스트가 {@link #MAX_SEMANTIC_CHARS}를 초과하거나 LLM 호출에 실패해도
 * 고정 토큰 크기 방식으로 자동 대체(fail-open)한다.
 *
 * <p><b>비용 주의</b>: 문서당 LLM 호출 1회 추가 발생.
 * 텍스트가 {@value MAX_SEMANTIC_CHARS}자를 초과하면 LLM 호출을 생략한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.chunk.strategy", havingValue = "semantic", matchIfMissing = true)
public class SemanticChunkSplitter implements ChunkSplitterPort {

    private final ChatClient          chatClient;
    private final RagProperties       ragProperties;
    private final ObjectMapper        objectMapper;
    private final ChunkSplitterPrompts prompts;

    /**
     * 시맨틱 청킹을 적용할 최대 문자 수.
     * 초과 시 LLM 호출 없이 고정 토큰 크기 방식으로 대체한다.
     */
    static final int MAX_SEMANTIC_CHARS = 20_000;

    @Override
    public List<Document> split(List<Document> docs) {
        List<Document> result = new ArrayList<>();
        for (Document doc : docs) {
            result.addAll(splitDocument(doc));
        }
        return result;
    }

    private List<Document> splitDocument(Document doc) {
        String text = doc.getText();
        if (text == null || text.isBlank()) {
            return List.of(doc);
        }

        if (text.length() <= MAX_SEMANTIC_CHARS) {
            List<String> chunks = requestChunksFromLlm(text);
            if (!chunks.isEmpty()) {
                log.info("시맨틱 청킹 완료: chunks={}", chunks.size());
                return chunks.stream()
                        .filter(c -> !c.isBlank())
                        .map(c -> new Document(c, new HashMap<>(doc.getMetadata())))
                        .toList();
            }
        }

        log.info("고정 크기 청킹으로 대체: textLength={}", text.length());
        return applyFixedSplitter(List.of(doc));
    }

    private List<String> requestChunksFromLlm(String text) {
        try {
            String response = chatClient.prompt()
                    .system(prompts.system())
                    .user(text)
                    .call()
                    .content();
            return parseChunks(response);
        } catch (Exception e) {
            log.warn("시맨틱 청킹 LLM 호출 실패 (고정 크기로 대체): {}", e.getMessage());
            return List.of();
        }
    }

    List<String> parseChunks(String responseText) {
        try {
            String json = extractJson(responseText);
            JsonNode node = objectMapper.readTree(json);
            JsonNode chunksNode = node.path("chunks");
            if (!chunksNode.isArray() || chunksNode.isEmpty()) {
                return List.of();
            }
            List<String> chunks = new ArrayList<>();
            for (JsonNode chunk : chunksNode) {
                String content = chunk.asText("").strip();
                if (!content.isBlank()) {
                    chunks.add(content);
                }
            }
            return chunks;
        } catch (Exception e) {
            log.warn("청킹 응답 파싱 실패 (고정 크기로 대체): response={}, error={}", responseText, e.getMessage());
            return List.of();
        }
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        text = text.strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private List<Document> applyFixedSplitter(List<Document> docs) {
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
