package com.jdh.rag.adapter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.jdh.rag.config.DouzoneEmbeddingProperties;
import com.jdh.rag.exception.LlmException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;
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
 * Douzone 사내 임베딩 API 어댑터. rag.embedding.type=douzone 시 활성화.
 *
 * 응답 포맷 자동 감지: embedding 필드 → vector 필드 → 직접 배열 → OpenAI 호환(data[0].embedding).
 * response-field 설정으로 우선 필드 고정 가능. 단건 API이므로 배치 수집 시 순차 호출.
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

    @Override
    public float[] embed(Document document) {
        return embedSingle(document.getText());
    }

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

    /** 응답 JSON에서 float 벡터 추출. response-field 우선, 이후 포맷 순서대로 시도. */
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

    /** 배열 노드 탐색: response-field → 직접 배열 → embedding 필드 → OpenAI data[0].embedding */
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

    /** Douzone 임베딩 API 전용 예외. LlmException 상속 → EMBEDDING_FAILED(L0002, 503) 처리. */
    public static class DouzoneEmbeddingException extends LlmException {
        public DouzoneEmbeddingException(String message) {
            super(RagExceptionEnum.EMBEDDING_FAILED, message);
        }
        public DouzoneEmbeddingException(String message, Throwable cause) {
            super(RagExceptionEnum.EMBEDDING_FAILED, message, cause);
        }
    }
}