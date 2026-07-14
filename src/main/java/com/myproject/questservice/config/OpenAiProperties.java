package com.myproject.questservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        String model,
        Integer connectTimeoutMs,
        Integer readTimeoutMs
) {
}

