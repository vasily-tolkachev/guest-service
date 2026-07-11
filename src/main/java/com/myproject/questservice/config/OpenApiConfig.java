package com.myproject.questservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI questServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Quest Service API")
                        .version("v1")
                        .description("Sprint 1 Quest Engine API"));
    }
}
