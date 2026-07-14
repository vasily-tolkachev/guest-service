package com.myproject.questservice.adapter.out.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myproject.questservice.application.port.out.generator.AiClient;
import com.myproject.questservice.application.service.AiGenerationException;
import com.myproject.questservice.config.OpenAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@Slf4j
@RequiredArgsConstructor
public class OpenAiAiClient implements AiClient {
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 500_000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OpenAiProperties properties;

    @Override
    public JsonNode generate(String systemPrompt, String userPrompt) {
        String apiKey = properties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiGenerationException("OpenAI API key is not configured");
        }

        String model = properties.model() == null || properties.model().isBlank()
                ? "gpt-5-mini"
                : properties.model();

        int connectTimeoutMs = properties.connectTimeoutMs() == null || properties.connectTimeoutMs() <= 0
                ? DEFAULT_CONNECT_TIMEOUT_MS
                : properties.connectTimeoutMs();
        int readTimeoutMs = properties.readTimeoutMs() == null || properties.readTimeoutMs() <= 0
                ? DEFAULT_READ_TIMEOUT_MS
                : properties.readTimeoutMs();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        RestClient restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory)
                .build();

        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(new ChatCompletionRequest(
                            model,
                            new Message[]{
                                    new Message("system", systemPrompt),
                                    new Message("user", userPrompt)
                            },
                            new ResponseFormat("json_object")
                    ))
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            if (response == null || response.choices() == null || response.choices().length == 0) {
                throw new AiGenerationException("OpenAI returned empty response");
            }

            String content = response.choices()[0].message().content();
            if (content == null || content.isBlank()) {
                throw new AiGenerationException("OpenAI response content is empty");
            }

            return OBJECT_MAPPER.readTree(content);
        } catch (RestClientResponseException ex) {
            log.error("OpenAI HTTP error status={} body={}", ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw new AiGenerationException("OpenAI request failed with status " + ex.getStatusCode(), ex);
        } catch (AiGenerationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("OpenAI request failed", ex);
            throw new AiGenerationException("OpenAI request failed", ex);
        }
    }

    private record ChatCompletionRequest(
            String model,
            Message[] messages,
            ResponseFormat response_format
    ) {
    }

    private record Message(
            String role,
            String content
    ) {
    }

    private record ResponseFormat(
            String type
    ) {
    }

    private record ChatCompletionResponse(
            Choice[] choices
    ) {
    }

    private record Choice(
            Message message
    ) {
    }
}
