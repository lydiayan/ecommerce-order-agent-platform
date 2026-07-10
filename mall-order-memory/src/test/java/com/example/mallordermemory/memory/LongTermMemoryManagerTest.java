package com.example.mallordermemory.memory;

import io.milvus.grpc.QueryResults;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class LongTermMemoryManagerTest {

    @Test
    void hasQueryRowsReturnsFalseForNullData() {
        assertFalse(LongTermMemoryManager.hasQueryRows(null));
    }

    @Test
    void hasQueryRowsReturnsFalseForEmptyQueryResults() {
        assertFalse(LongTermMemoryManager.hasQueryRows(QueryResults.newBuilder().build()));
    }
}
