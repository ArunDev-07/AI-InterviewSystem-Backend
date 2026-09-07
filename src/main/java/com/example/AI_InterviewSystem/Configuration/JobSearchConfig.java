package com.example.AI_InterviewSystem.Configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Adzuna job search API client. Adzuna auth is unusual — app_id and app_key are
 * passed as QUERY PARAMETERS on every request, not headers (no Authorization
 * header method exists for this API). So unlike GithubConfig, there's no default
 * auth header to set here — JobSearchService appends app_id/app_key to each
 * request's query string itself.
 *
 * Base URL includes no country — that's part of the path per-request
 * (e.g. /v1/api/jobs/in/search/1 for India), so JobSearchService builds the
 * full path per call.
 */
@Configuration
public class JobSearchConfig {

    @Bean
    public RestClient adzunaRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.adzuna.com")
                .build();
    }
}