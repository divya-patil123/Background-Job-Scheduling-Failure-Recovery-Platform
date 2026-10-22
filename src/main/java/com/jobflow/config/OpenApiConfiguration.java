package com.jobflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI jobFlowOpenApi() {
        return new OpenAPI().info(new Info()
                .title("JobFlow API")
                .version("1.0.0")
                .description("Background job scheduling, execution history, retries, and alerts."));
    }
}
