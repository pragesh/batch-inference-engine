package com.batchinference.service;

import com.batchinference.model.PromptItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
