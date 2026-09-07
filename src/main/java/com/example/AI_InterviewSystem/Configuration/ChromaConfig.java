package com.example.AI_InterviewSystem.Configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ChromaConfig {

    @Value("${chroma.base-url}")
    private String chromaBaseUrl;

    @Bean
    public RestClient chromaRestClient() {
        return RestClient.builder()
                .baseUrl(chromaBaseUrl)
                .build();
    }
}