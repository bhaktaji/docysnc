package com.docsync.common.adapter.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "docsync.llm.provider", havingValue = "mock")
public class MockLlmClient implements LlmClient {

    private String response = "# Updated documentation\n\nGenerated content.\n";

    public void setResponse(String response) {
        this.response = response;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        return new LlmResponse(response, 10, 20, 30, request.model(), Duration.ofMillis(5));
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
