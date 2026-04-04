package com.jdh.rag.adapter;

import tools.jackson.databind.ObjectMapper;
import com.jdh.rag.config.DouzoneEmbeddingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DouzoneEmbeddingModelTest {

    // RestClient를 직접 mock하기 어려우므로, 파싱 로직만 단위 테스트하고
    // HTTP 통신 테스트는 WireMock 등 별도 통합 테스트로 분리한다.

    private DouzoneEmbeddingModelTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new DouzoneEmbeddingModelTestHelper();
    }

    @Test
    @DisplayName("embedding 필드 포맷 파싱")
    void embedding_필드_포맷_파싱() throws Exception {
        String json = """
                {"embedding": [0.1, 0.2, 0.3, 0.4]}
                """;

        float[] result = helper.parseEmbedding(json, "embedding");

        assertThat(result).hasSize(4);
        assertThat(result[0]).isEqualTo(0.1f, within(1e-5f));
        assertThat(result[1]).isEqualTo(0.2f, within(1e-5f));
    }

    @Test
    @DisplayName("직접 배열 포맷 파싱")
    void 직접_배열_포맷_파싱() throws Exception {
        String json = "[0.5, 0.6, 0.7]";

        float[] result = helper.parseEmbedding(json, "");

        assertThat(result).hasSize(3);
        assertThat(result[0]).isEqualTo(0.5f, within(1e-5f));
    }

    @Test
    @DisplayName("OpenAI 호환 포맷 파싱")
    void OpenAI_호환_포맷_파싱() throws Exception {
        String json = """
                {
                  "data": [
                    {"embedding": [0.9, 0.8, 0.7], "index": 0}
                  ]
                }
                """;

        // response-field 설정이 맞지 않아도 OpenAI 호환 포맷 감지
        float[] result = helper.parseEmbedding(json, "nonexistent");

        assertThat(result).hasSize(3);
        assertThat(result[0]).isEqualTo(0.9f, within(1e-5f));
    }

    @Test
    @DisplayName("vector 필드 포맷 파싱")
    void vector_필드_포맷_파싱() throws Exception {
        String json = """
                {"vector": [1.0, 2.0, 3.0]}
                """;

        float[] result = helper.parseEmbedding(json, "vector");

        assertThat(result).hasSize(3);
        assertThat(result[0]).isEqualTo(1.0f, within(1e-5f));
    }

    @Test
    @DisplayName("알 수 없는 포맷이면 예외 발생")
    void 알_수_없는_포맷이면_예외_발생() {
        String json = """
                {"unknown_field": "not an array"}
                """;

        assertThatThrownBy(() -> helper.parseEmbedding(json, "embedding"))
                .isInstanceOf(DouzoneEmbeddingModel.DouzoneEmbeddingException.class)
                .hasMessageContaining("임베딩 배열을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("잘못된 JSON이면 예외 발생")
    void 잘못된_JSON이면_예외_발생() {
        assertThatThrownBy(() -> helper.parseEmbedding("not-json", "embedding"))
                .isInstanceOf(DouzoneEmbeddingModel.DouzoneEmbeddingException.class);
    }

    @Test
    @DisplayName("dimensions 반환")
    void dimensions_반환() {
        DouzoneEmbeddingProperties props = mockProps("https://test.url", 1536, "embedding");
        DouzoneEmbeddingModel model = new DouzoneEmbeddingModel(props, new ObjectMapper());

        assertThat(model.dimensions()).isEqualTo(1536);
    }

    /**
     * DouzoneEmbeddingModel의 파싱 로직을 직접 테스트하기 위한 헬퍼.
     * RestClient 의존성 없이 parseEmbedding() 동작만 검증한다.
     */
    static class DouzoneEmbeddingModelTestHelper {
        private final ObjectMapper objectMapper = new ObjectMapper();

        float[] parseEmbedding(String json, String responseField) throws Exception {
            DouzoneEmbeddingProperties props = mockProps("https://test.url", 1024, responseField);
            DouzoneEmbeddingModel model = new DouzoneEmbeddingModel(props, objectMapper);

            // reflection으로 private parseEmbedding 호출
            var method = DouzoneEmbeddingModel.class
                    .getDeclaredMethod("parseEmbedding", String.class);
            method.setAccessible(true);
            try {
                return (float[]) method.invoke(model, json);
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) throw re;
                throw e;
            }
        }
    }

    private static DouzoneEmbeddingProperties mockProps(String url, int dims, String field) {
        DouzoneEmbeddingProperties props = mock(DouzoneEmbeddingProperties.class);
        when(props.url()).thenReturn(url);
        when(props.dimensions()).thenReturn(dims);
        when(props.responseField()).thenReturn(field);
        when(props.connectTimeout()).thenReturn(5000);
        when(props.readTimeout()).thenReturn(30000);
        return props;
    }
}
