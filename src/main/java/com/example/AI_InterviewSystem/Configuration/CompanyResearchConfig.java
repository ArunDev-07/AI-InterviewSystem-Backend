package com.example.AI_InterviewSystem.Configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Tavily web search API client. Simplest of the three external API integrations —
 * single endpoint, Bearer token auth (same pattern as GithubConfig), JSON body
 * request rather than Adzuna's query-param scheme.
 *
 * If tavily.api-key is left blank, CompanyResearchService throws a clear
 * "not configured" exception that CompanyResearchTool turns into a message
 * for the agent — same optional-tool failure isolation as GitHub/job search.
 */
@Configuration
public class CompanyResearchConfig {

    @Value("${tavily.api-key:}")
    private String tavilyApiKey;

    @Bean
    public RestClient tavilyRestClient() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.tavily.com");

        if (tavilyApiKey != null && !tavilyApiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + tavilyApiKey);
        }

        return builder.build();
    }
}