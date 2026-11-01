package com.docsync.common.adapter.llm;

import java.time.Duration;
import java.util.Map;

public interface LlmClient {

    LlmResponse complete(LlmRequest request);

    boolean isAvailable();

    record LlmRequest(String systemPrompt, String userPrompt, String model, double temperature,
                      int maxTokens, Map<String, String> metadata) {
    }

    record LlmResponse(String content, int promptTokens, int completionTokens, int totalTokens,
                       String model, Duration latency) {
    }
}
