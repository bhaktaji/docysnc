package com.docsync.common.adapter.llm;

import com.docsync.common.exception.ExternalServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "docsync.llm.provider", havingValue = "openai")
public class OpenAiLlmClient implements LlmClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;

    public OpenAiLlmClient(RestClient.Builder builder,
                           @Value("${docsync.llm.api-key:}") String apiKey,
                           @Value("${docsync.llm.model:gpt-4}") String model,
                           @Value("${docsync.llm.base-url:https://api.openai.com/v1}") String baseUrl,
                           @Value("${docsync.llm.timeout-seconds:60}") int timeoutSeconds) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (!isAvailable()) {
            throw new ExternalServiceException("LLM", "LLM provider is not configured");
        }
        long start = System.nanoTime();
        try {
            OpenAiResponse response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", request.model() == null || request.model().isBlank() ? model : request.model(),
                            "messages", List.of(
                                    Map.of("role", "system", "content", request.systemPrompt()),
                                    Map.of("role", "user", "content", request.userPrompt())),
                            "temperature", request.temperature(),
                            "max_tokens", request.maxTokens()))
                    .retrieve()
                    .body(OpenAiResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new ExternalServiceException("LLM", "LLM response was empty");
            }
            String content = response.choices().get(0).message().content();
            int promptTokens = response.usage() == null ? 0 : response.usage().promptTokens();
            int completionTokens = response.usage() == null ? 0 : response.usage().completionTokens();
            return new LlmResponse(content, promptTokens, completionTokens,
                    promptTokens + completionTokens, model, Duration.ofNanos(System.nanoTime() - start));
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ExternalServiceException("LLM", "LLM request failed", ex);
        }
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenAiResponse(List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("prompt_tokens") int promptTokens,
                 @JsonProperty("completion_tokens") int completionTokens) {
    }
}
