package com.jdh.rag.service;

import com.jdh.rag.domain.IngestionRequest;
import com.jdh.rag.domain.IngestionResult;
import com.jdh.rag.exception.IngestionException;
import com.jdh.rag.exception.common.enums.RagExceptionEnum;
import com.jdh.rag.port.ChunkSplitterPort;
import com.jdh.rag.port.KeywordIndexPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Captor;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IngestionServiceTest {

    @Captor private ArgumentCaptor<List<String>> idsCaptor;

    @Mock private VectorStore vectorStore;
    @Mock private KeywordIndexPort keywordIndexPort;
    @Mock private ChunkSplitterPort chunkSplitterPort;

    private IngestionService service;

    @BeforeEach
    void setUp() {
        // 청킹을 통과(입력 그대로 반환)시켜 IngestionService 로직에 집중한다.
        // LENIENT: 빈 content 등 예외가 split 호출 전 throw되는 테스트에서 미사용 stubbing 허용
        when(chunkSplitterPort.split(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service = new IngestionService(vectorStore, keywordIndexPort, chunkSplitterPort);
    }

    // ── ingest (텍스트) ────────────────────────────────────────────────────────

    @Test
    @DisplayName("빈 content이면 IngestionException(EMPTY_CONTENT)을 던진다")
    void blankContent_throwsIngestionException() {
        IngestionRequest request = request("doc-001", "");

        assertThatThrownBy(() -> service.ingest(request))
                .isInstanceOf(IngestionException.class)
                .extracting(e -> ((IngestionException) e).getExceptionEnum())
                .isEqualTo(RagExceptionEnum.EMPTY_CONTENT);
    }

    @Test
    @DisplayName("null content이면 IngestionException(EMPTY_CONTENT)을 던진다")
    void nullContent_throwsIngestionException() {
        IngestionRequest request = request("doc-001", null);

        assertThatThrownBy(() -> service.ingest(request))
                .isInstanceOf(IngestionException.class)
                .extracting(e -> ((IngestionException) e).getExceptionEnum())
                .isEqualTo(RagExceptionEnum.EMPTY_CONTENT);
    }

    @Test
    @DisplayName("공백 content이면 IngestionException(EMPTY_CONTENT)을 던진다")
    void whitespaceContent_throwsIngestionException() {
        IngestionRequest request = request("doc-001", "   \t\n  ");

        assertThatThrownBy(() -> service.ingest(request))
                .isInstanceOf(IngestionException.class)
                .extracting(e -> ((IngestionException) e).getExceptionEnum())
                .isEqualTo(RagExceptionEnum.EMPTY_CONTENT);
    }

    @Test
    @DisplayName("vectorStore.add 실패 시 IngestionException(VECTOR_STORE_FAILED)을 던진다")
    void vectorStoreFailure_throwsIngestionException() {
        doThrow(new RuntimeException("PgVector 연결 실패"))
                .when(vectorStore).add(anyList());

        IngestionRequest request = request("doc-001", "충분히 긴 내용입니다. ".repeat(10));

        assertThatThrownBy(() -> service.ingest(request))
                .isInstanceOf(IngestionException.class)
                .extracting(e -> ((IngestionException) e).getExceptionEnum())
                .isEqualTo(RagExceptionEnum.VECTOR_STORE_FAILED);
    }

    @Test
    @DisplayName("IngestionException에 원인 예외가 포함된다")
    void vectorStoreFailure_causeIsPreserved() {
        RuntimeException cause = new RuntimeException("PgVector 연결 실패");
        doThrow(cause).when(vectorStore).add(anyList());

        IngestionRequest request = request("doc-001", "충분히 긴 내용입니다. ".repeat(10));

        assertThatThrownBy(() -> service.ingest(request))
                .isInstanceOf(IngestionException.class)
                .hasCause(cause);
    }

    @Test
    @DisplayName("키워드 색인 실패 시 벡터 저장을 롤백하고 IngestionException(INGESTION_FAILED)을 던진다")
    void keywordIndexFailure_rollsBackVectorStore() {
        doThrow(new RuntimeException("BM25 색인 실패"))
                .when(keywordIndexPort).index(anyList());

        IngestionRequest request = request("doc-rollback", "충분히 긴 내용입니다. ".repeat(10));

        assertThatThrownBy(() -> service.ingest(request))
                .isInstanceOf(IngestionException.class)
                .extracting(e -> ((IngestionException) e).getExceptionEnum())
                .isEqualTo(RagExceptionEnum.INGESTION_FAILED);

        verify(vectorStore).delete(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly("doc-rollback-0");
    }

    @Test
    @DisplayName("정상 수집 시 IngestionResult를 반환한다")
    void normalIngest_returnsIngestionResult() {
        IngestionRequest request = request("doc-001", "충분히 긴 내용입니다. ".repeat(10));

        IngestionResult result = service.ingest(request);

        assertThat(result.docId()).isEqualTo("doc-001");
        assertThat(result.chunkCount()).isGreaterThan(0);
        verify(vectorStore, times(1)).add(anyList());
        verify(keywordIndexPort, times(1)).index(anyList());
    }

    @Test
    @DisplayName("정상 수집 시 chunkId가 docId 기반으로 생성된다")
    void normalIngest_chunkIdIsDocIdBased() {
        IngestionRequest request = request("law-doc", "충분히 긴 내용입니다. ".repeat(10));

        service.ingest(request);

        verify(keywordIndexPort).index(argThat(hits ->
                hits.stream().allMatch(h -> h.id().startsWith("law-doc-"))
        ));
    }

    @Test
    @DisplayName("정상 수집 시 VectorStore에는 chunkId와 동일한 document id가 저장된다")
    void normalIngest_vectorStoreDocumentIdMatchesChunkId() {
        IngestionRequest request = request("law-doc", "충분히 긴 내용입니다. ".repeat(10));

        service.ingest(request);

        verify(vectorStore).add(argThat(docs ->
                docs.stream().allMatch(doc -> doc.getId().startsWith("law-doc-"))
        ));
    }

    // ── ingestResource (파일) ──────────────────────────────────────────────────

    @Test
    @DisplayName("ingestResource에서 Resource.getInputStream 실패 시 IngestionException(FILE_PARSE_FAILED)을 던진다")
    void resourceInputStreamFailure_throwsFileParseFailed() throws IOException {
        Resource mockResource = mock(Resource.class);
        when(mockResource.getFilename()).thenReturn("broken.pdf");
        when(mockResource.getInputStream()).thenThrow(new IOException("파일 읽기 실패"));

        IngestionRequest req = request("doc-file", "");

        assertThatThrownBy(() -> service.ingestResource(mockResource, req))
                .isInstanceOf(IngestionException.class)
                .extracting(e -> ((IngestionException) e).getExceptionEnum())
                .isEqualTo(RagExceptionEnum.FILE_PARSE_FAILED);
    }

    @Test
    @DisplayName("ingestResource 파일 파싱 실패 예외에 파일명이 포함된다")
    void resourceInputStreamFailure_messageContainsFilename() throws IOException {
        Resource mockResource = mock(Resource.class);
        when(mockResource.getFilename()).thenReturn("broken.pdf");
        when(mockResource.getInputStream()).thenThrow(new IOException("읽기 오류"));

        assertThatThrownBy(() -> service.ingestResource(mockResource, request("doc-001", "")))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("broken.pdf");
    }

    @Test
    @DisplayName("ingestResource에서 vectorStore.add 실패 시 IngestionException(VECTOR_STORE_FAILED)을 던진다")
    void ingestResource_vectorStoreFailure_throwsException() {
        doThrow(new RuntimeException("PgVector 오류")).when(vectorStore).add(anyList());

        // 충분한 텍스트를 포함한 plain text 리소스
        Resource resource = textResource("doc-file", "긴 텍스트 내용 ".repeat(20));

        IngestionRequest req = request("doc-file", "");

        assertThatThrownBy(() -> service.ingestResource(resource, req))
                .isInstanceOf(IngestionException.class)
                .extracting(e -> ((IngestionException) e).getExceptionEnum())
                .isEqualTo(RagExceptionEnum.VECTOR_STORE_FAILED);
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private IngestionRequest request(String docId, String content) {
        return new IngestionRequest(docId, "source.txt", "faq", "1.0", "default", content);
    }

    /** 파일명을 지정한 plain text ByteArrayResource */
    private Resource textResource(String filename, String content) {
        return new ByteArrayResource(content.getBytes()) {
            @Override public String getFilename() { return filename + ".txt"; }
        };
    }
}
