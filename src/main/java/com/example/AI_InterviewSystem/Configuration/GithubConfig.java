package com.example.AI_InterviewSystem.Configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * GitHub REST API client. Same pattern as ChromaConfig (RestClient bean with
 * base URL), so this fits your existing style rather than introducing a new one.
 *
 * The token is a Personal Access Token with NO scopes needed (public data only:
 * profile + public repos). Never exposed to React — stays in this backend config,
 * read from application.properties / env var like your other API keys (groq.api.key etc).
 *
 * If github.token is left blank, GithubService still works but is subject to GitHub's
 * much lower unauthenticated rate limit (60 req/hr vs 5,000 req/hr authenticated) —
 * fine for local dev/testing, not for anything with real traffic.
 */
@Configuration
public class GithubConfig {

    @Value("${github.token:}")
    private String githubToken;

    @Bean
    public RestClient githubRestClient() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");

        if (githubToken != null && !githubToken.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + githubToken);
        }

        return builder.build();
    }
}