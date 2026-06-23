package com.batchinference.service;

import com.batchinference.model.PromptItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchFileReaderTest {

    private final BatchFileReader reader = new BatchFileReader(new ObjectMapper());

    @Test
    void streamsChunksWithoutLoadingEntireFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("batch.json");
        String json = """
                [
                  {"id":"a","prompt":"one"},
                  {"id":"b","prompt":"two"},
                  {"id":"c","prompt":"three"},
                  {"id":"d","prompt":"four"}
                ]
                """;
        Files.writeString(file, json);

        List<List<PromptItem>> chunks = new ArrayList<>();
        reader.streamChunks(file, 2, chunks::add);

        assertEquals(2, chunks.size());
        assertEquals(2, chunks.get(0).size());
        assertEquals(2, chunks.get(1).size());
        assertEquals("a", chunks.get(0).get(0).id());
        assertEquals("d", chunks.get(1).get(1).id());
        assertEquals(4, reader.countItems(file));
    }

    @Test
    void streamsLargeBatchInFixedSizeChunks(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("large_batch.json");
        StringBuilder json = new StringBuilder("[\n");
        int itemCount = 250;
        for (int i = 0; i < itemCount; i++) {
            if (i > 0) {
                json.append(",\n");
            }
            json.append("{\"id\":\"item-").append(i).append("\",\"prompt\":\"prompt ").append(i).append("\"}");
        }
        json.append("\n]");
        Files.writeString(file, json);

        int chunkSize = 50;
        List<List<PromptItem>> chunks = new ArrayList<>();
        reader.streamChunks(file, chunkSize, chunks::add);

        assertEquals(5, chunks.size());
        assertEquals(chunkSize, chunks.get(0).size());
        assertEquals(chunkSize, chunks.get(4).size());
        assertEquals(itemCount, reader.countItems(file));
    }

    @Test
    void rejectsNonArrayRoot(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("invalid.json");
        Files.writeString(file, "{\"id\":\"only-object\"}");

        IOException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IOException.class,
                () -> reader.streamChunks(file, 10, chunk -> {})
        );
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("Expected JSON array"));
    }
}
