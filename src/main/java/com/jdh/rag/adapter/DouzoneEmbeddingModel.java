package com.jdh.rag.adapter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.jdh.rag.config.DouzoneEmbeddingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Douzone 사내 임베딩 API 어댑터.
 *
 * <pre>
 * POST https://private-ai.douzone.com/ailab-embedding-8
 * Content-Type: application/json
 * { "input": "텍스트" }
 * </pre>
 *
 * rag.embedding.type=douzone 로 설정하면 OpenAI EmbeddingModel 대신 이 빈이 사용된다.
 *
 * 응답 포맷 자동 감지 (rag.embedding.douzone.response-field 로 고정도 가능):
 *   1. { "embedding": [...] }
 *   2. { "vector": [...] }      ← response-field 변경 시
 *   3. [...]                    ← response-field="" 로 설정 시
 *   4. { "data": [{"embedding": [...]}] }  ← OpenAI 호환 포맷
 *
 * 주의: 단건(single) API라서 배치 임베딩 시 텍스트 수만큼 API를 순차 호출한다.
 */
@Slf4j
@Primary
@Component
@ConditionalOnProperty(name = "rag.embedding.type", havingValue = "douzone")
public class DouzoneEmbeddingModel implements EmbeddingModel {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DouzoneEmbeddingProperties props;

    private record EmbedRequest(String input) {}

    public DouzoneEmbeddingModel(DouzoneEmbeddingProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(props.url())
                .build();
        log.info("Douzone 임베딩 모델 초기화: url={}, dimensions={}", props.url(), props.dimensions());
    }

    // ── EmbeddingModel 구현 ────────────────────────────────────────────────────

    /**
     * 배치 임베딩 요청.
     * 내부적으로 각 텍스트를 단건 API 호출로 처리한다.
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(texts.size());

        for (int i = 0; i < texts.size(); i++) {
            float[] vector = embedSingle(texts.get(i));
            embeddings.add(new Embedding(vector, i));
        }

        log.info("Douzone 임베딩 완료: {}건", texts.size());
        return new EmbeddingResponse(embeddings);
    }

    /**
     * Document 임베딩 (텍스트 추출 후 단건 API 호출).
     */
    @Override
    public float[] embed(Document document) {
        return embedSingle(document.getText());
    }

    /**
     * 벡터 차원 수 반환.
     * API 호출 없이 설정값을 반환한다.
     */
    @Override
    public int dimensions() {
        return props.dimensions();
    }

    // ── 내부 처리 ──────────────────────────────────────────────────────────────

    private float[] embedSingle(String text) {
        String responseBody = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbedRequest(text))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), (req, resp) -> {
                    throw new DouzoneEmbeddingException(
                            "Douzone 임베딩 API 오류: HTTP " + resp.getStatusCode()
                    );
                })
                .body(String.class);

        return parseEmbedding(responseBody);
    }

    /**
     * 응답 JSON에서 float 벡터를 추출한다.
     * response-field 설정값을 기준으로 파싱하되, 알려진 포맷을 순서대로 시도한다.
     */
    private float[] parseEmbedding(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arrayNode = resolveArrayNode(root);

            if (arrayNode == null || !arrayNode.isArray()) {
                throw new DouzoneEmbeddingException(
                        "임베딩 배열을 찾을 수 없습니다. 응답: " + json
                );
            }

            float[] vector = new float[arrayNode.size()];
            for (int i = 0; i < arrayNode.size(); i++) {
                vector[i] = (float) arrayNode.get(i).asDouble();
            }
            return vector;

        } catch (DouzoneEmbeddingException e) {
            throw e;
        } catch (Exception e) {
            throw new DouzoneEmbeddingException("임베딩 파싱 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 응답 JSON에서 배열 노드를 찾는다.
     * 우선순위: 설정된 response-field → 직접 배열 → OpenAI 호환 포맷
     */
    private JsonNode resolveArrayNode(JsonNode root) {
        String field = props.responseField();

        // 1) response-field가 설정된 경우
        if (field != null && !field.isBlank()) {
            JsonNode node = root.get(field);
            if (node != null && node.isArray()) return node;
        }

        // 2) 응답 자체가 배열 (response-field="" 또는 직접 배열 응답)
        if (root.isArray()) return root;

        // 3) "embedding" 필드 (response-field가 다르게 설정됐어도 시도)
        if (root.has("embedding") && root.get("embedding").isArray()) {
            return root.get("embedding");
        }

        // 4) OpenAI 호환: { "data": [{"embedding": [...]}] }
        if (root.has("data")) {
            JsonNode data = root.get("data");
            if (data.isArray() && data.size() > 0 && data.get(0).has("embedding")) {
                return data.get(0).get("embedding");
            }
        }

        return null;
    }

    // ── 커스텀 예외 ─────────────────────────────────────────────────────────────

    public static class DouzoneEmbeddingException extends RuntimeException {
        public DouzoneEmbeddingException(String message) { super(message); }
        public DouzoneEmbeddingException(String message, Throwable cause) { super(message, cause); }
    }
}