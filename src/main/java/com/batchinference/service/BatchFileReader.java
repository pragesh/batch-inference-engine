package com.batchinference.service;

import com.batchinference.model.PromptItem;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Streams prompt records from a JSON array file without loading the full dataset into memory.
 * <p>
 * Supports multiple input shapes: {@code {id, prompt}}, {@code {recordId, modelInput.prompt}},
 * or chat-style {@code messages} arrays. Designed to keep heap usage O(chunk size) for large batches.
 */
@Component
public class BatchFileReader {

    private final ObjectMapper objectMapper;

    public BatchFileReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Reads the file as a top-level JSON array and delivers fixed-size chunks to the consumer.
     *
     * @param filePath      path to the batch input file
     * @param chunkSize     maximum records per chunk
     * @param chunkConsumer callback invoked once per chunk
     */
    public void streamChunks(Path filePath, int chunkSize, Consumer<List<PromptItem>> chunkConsumer) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath);
             JsonParser parser = new JsonFactory().createParser(inputStream)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("Expected JSON array at root of " + filePath);
            }

            List<PromptItem> chunk = new ArrayList<>(chunkSize);
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                JsonNode node = objectMapper.readTree(parser);
                PromptItem item = toPromptItem(node);
                chunk.add(item);
                if (chunk.size() >= chunkSize) {
                    chunkConsumer.accept(List.copyOf(chunk));
                    chunk.clear();
                }
            }
            if (!chunk.isEmpty()) {
                chunkConsumer.accept(List.copyOf(chunk));
            }
        }
    }

    /**
     * Counts array elements without deserializing record contents.
     */
    public long countItems(Path filePath) throws IOException {
        long count = 0;
        try (InputStream inputStream = Files.newInputStream(filePath);
             JsonParser parser = new JsonFactory().createParser(inputStream)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("Expected JSON array at root of " + filePath);
            }
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                parser.skipChildren();
                count++;
            }
        }
        return count;
    }

    private PromptItem toPromptItem(JsonNode node) throws IOException {
        String id = textField(node, "id");
        if (id == null) {
            id = textField(node, "recordId");
        }
        if (id == null) {
            throw new IOException("Prompt item missing id/recordId");
        }

        String prompt = textField(node, "prompt");
        if (prompt == null) {
            prompt = textField(node.path("modelInput"), "prompt");
        }
        if (prompt == null && node.has("messages")) {
            prompt = extractFromMessages(node.get("messages"));
        }
        if (prompt == null) {
            throw new IOException("Prompt item " + id + " missing prompt content");
        }
        return new PromptItem(id, prompt);
    }

    private String extractFromMessages(JsonNode messages) {
        if (!messages.isArray()) {
            return null;
        }
        Iterator<JsonNode> iterator = messages.elements();
        while (iterator.hasNext()) {
            JsonNode message = iterator.next();
            if ("user".equalsIgnoreCase(message.path("role").asText())) {
                return message.path("content").asText(null);
            }
        }
        return null;
    }

    private String textField(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.has(field)) {
            return null;
        }
        String value = node.get(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
