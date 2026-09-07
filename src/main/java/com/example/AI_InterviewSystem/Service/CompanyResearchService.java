package com.example.AI_InterviewSystem.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CompanyResearchService {

    private final RestClient tavilyRestClient;

    @Value("${tavily.api-key:}")
    private String tavilyApiKey;

    private static final int MAX_RESULTS = 5;

    public CompanyResearchService(RestClient tavilyRestClient) {
        this.tavilyRestClient = tavilyRestClient;
    }

    public static class TavilyNotConfiguredException extends RuntimeException {
        public TavilyNotConfiguredException() {
            super("Tavily API key is not configured — company research is unavailable.");
        }
    }

    public static class TavilyRateLimitException extends RuntimeException {
        public TavilyRateLimitException() {
            super("Tavily API rate/credit limit reached. Try again later.");
        }
    }

    public record SourceSnippet(String title, String url, String content) {}

    public record CompanyResearchResult(
            String answer,
            List<SourceSnippet> sources
    ) {}

    @SuppressWarnings("unchecked")
    public CompanyResearchResult researchCompany(String companyName) {
        if (tavilyApiKey == null || tavilyApiKey.isBlank()) {
            throw new TavilyNotConfiguredException();
        }

        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("Company name cannot be empty");
        }

        // Query built to surface what a candidate actually needs before an interview:
        // what the company does, its tech stack, and interview process — not just
        // general news.
        String query = companyName + " company overview technology stack interview process";

        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("search_depth", "basic"); // "advanced" costs 2x credits — basic is enough here
        body.put("max_results", MAX_RESULTS);
        body.put("include_answer", true); // Tavily's own synthesized summary — cheap grounding
        body.put("topic", "general");

        Map<String, Object> response;
        try {
            response = tavilyRestClient.post()
                    .uri("/search")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new TavilyRateLimitException();
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new TavilyNotConfiguredException();
        }

        if (response == null) {
            return new CompanyResearchResult(null, List.of());
        }

        String answer = response.get("answer") == null ? null : response.get("answer").toString();

        List<Map<String, Object>> rawResults = (List<Map<String, Object>>) response.get("results");
        List<SourceSnippet> sources = rawResults == null
                ? List.of()
                : rawResults.stream()
                .map(this::toSourceSnippet)
                .collect(Collectors.toList());

        return new CompanyResearchResult(answer, sources);
    }

    private SourceSnippet toSourceSnippet(Map<String, Object> result) {
        return new SourceSnippet(
                stringOrNull(result.get("title")),
                stringOrNull(result.get("url")),
                stringOrNull(result.get("content"))
        );
    }

    private String stringOrNull(Object o) {
        return o == null ? null : o.toString();
    }
}