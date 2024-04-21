package org.springframework.ai.llmsfreeapi.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class EmbeddingController {

    private final LLMsFreeApiEmbeddingClient embeddingClient;

    @Autowired
    public EmbeddingController(LLMsFreeApiEmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    @GetMapping("/ai/embedding")
    public Map embedding(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        return Map.of("embeddings", embeddingClient.embed(message));
    }

}
