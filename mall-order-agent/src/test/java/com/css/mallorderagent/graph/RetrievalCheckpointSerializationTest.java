package com.css.mallorderagent.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.example.mallordermilvusrag.dto.DocumentMetadata;
import com.example.mallordermilvusrag.dto.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalCheckpointSerializationTest {

    @Test
    void graphCheckpoint_roundTripsMatchedChunksAsTypedDtos() throws Exception {
        SearchResponse response = retrievalWithMatchedChunk();
        SpringAIJacksonStateSerializer serializer = new SpringAIJacksonStateSerializer(
                OverAllState::new, new ObjectMapper());

        OverAllState restoredState = serializer.cloneObject(Map.of(AgentGraphKeys.RETRIEVAL, response));
        SearchResponse restored = restoredState
                .value(AgentGraphKeys.RETRIEVAL, SearchResponse.class)
                .orElseThrow();

        assertInstanceOf(SearchResponse.MatchedChunk.class,
                restored.getHits().get(0).getMatchedChunks().get(0));
        assertEquals(response.getHits().get(0).getMatchedChunks(),
                restored.getHits().get(0).getMatchedChunks());
        assertDoesNotThrow(() -> serializer.cloneObject(restoredState.data()));
    }

    @Test
    void publicJson_doesNotExposeGraphTypeMetadata() throws Exception {
        String json = new ObjectMapper().writeValueAsString(retrievalWithMatchedChunk());

        assertTrue(json.contains("\"matchedChunks\""));
        assertFalse(json.contains("@class"));
    }

    private static SearchResponse retrievalWithMatchedChunk() {
        SearchResponse.SearchHit hit = new SearchResponse.SearchHit(
                "parent-1", "parent content", 0.92, new DocumentMetadata());
        hit.setMatchedChunks(List.of(new SearchResponse.MatchedChunk(
                "child-1", "child content", 0.92, 1, 10, 20)));
        return new SearchResponse("refund policy", 1, List.of(hit));
    }
}
