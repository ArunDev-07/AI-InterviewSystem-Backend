package com.example.AI_InterviewSystem.Service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GithubService {

    private final RestClient githubRestClient;

    public GithubService(RestClient githubRestClient) {
        this.githubRestClient = githubRestClient;
    }

    /** Thrown for a GitHub username that doesn't exist — distinct from generic API failure. */
    public static class GithubUserNotFoundException extends RuntimeException {
        public GithubUserNotFoundException(String username) {
            super("No GitHub user found with username: " + username);
        }
    }

    /** Thrown when GitHub's rate limit is hit (60/hr unauthenticated, 5000/hr with a token). */
    public static class GithubRateLimitException extends RuntimeException {
        public GithubRateLimitException() {
            super("GitHub API rate limit exceeded. Try again later, or configure github.token for a higher limit.");
        }
    }

    public record GithubProfile(
            String username,
            String bio,
            int publicRepos,
            int followers
    ) {}

    public record GithubRepoSummary(
            String name,
            String description,
            String primaryLanguage,
            int stars,
            List<String> topics
    ) {}

    public record GithubAnalysis(
            GithubProfile profile,
            List<GithubRepoSummary> topRepos,
            Map<String, Long> languageCounts
    ) {}

    private static final int MAX_REPOS_TO_FETCH = 20;
    private static final int MAX_TOP_REPOS_RETURNED = 6;

    @SuppressWarnings("unchecked")
    public GithubAnalysis analyzeUser(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("GitHub username cannot be empty");
        }

        Map<String, Object> profileResponse;
        try {
            profileResponse = githubRestClient.get()
                    .uri("/users/{username}", username)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new GithubUserNotFoundException(username);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new GithubRateLimitException();
        }

        if (profileResponse == null) {
            throw new GithubUserNotFoundException(username);
        }

        GithubProfile profile = new GithubProfile(
                username,
                stringOrNull(profileResponse.get("bio")),
                intOrZero(profileResponse.get("public_repos")),
                intOrZero(profileResponse.get("followers"))
        );

        List<Map<String, Object>> repos;
        try {
            repos = githubRestClient.get()
                    .uri("/users/{username}/repos?sort=updated&per_page={perPage}", username, MAX_REPOS_TO_FETCH)
                    .retrieve()
                    .body(List.class);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new GithubRateLimitException();
        }

        if (repos == null) {
            repos = List.of();
        }

        // Exclude forks — a candidate's forked repos aren't their own work, and
        // including them would misrepresent skill/project ownership to the agent.
        List<Map<String, Object>> ownRepos = repos.stream()
                .filter(r -> !Boolean.TRUE.equals(r.get("fork")))
                .collect(Collectors.toList());

        Map<String, Long> languageCounts = ownRepos.stream()
                .map(r -> stringOrNull(r.get("language")))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(lang -> lang, Collectors.counting()));

        List<GithubRepoSummary> topRepos = ownRepos.stream()
                .sorted(Comparator.comparingInt((Map<String, Object> r) -> intOrZero(r.get("stargazers_count"))).reversed())
                .limit(MAX_TOP_REPOS_RETURNED)
                .map(r -> new GithubRepoSummary(
                        stringOrNull(r.get("name")),
                        stringOrNull(r.get("description")),
                        stringOrNull(r.get("language")),
                        intOrZero(r.get("stargazers_count")),
                        topicsOrEmpty(r.get("topics"))
                ))
                .collect(Collectors.toList());

        return new GithubAnalysis(profile, topRepos, languageCounts);
    }

    private String stringOrNull(Object o) {
        return o == null ? null : o.toString();
    }

    private int intOrZero(Object o) {
        if (o instanceof Number n) return n.intValue();
        return 0;
    }

    @SuppressWarnings("unchecked")
    private List<String> topicsOrEmpty(Object o) {
        if (o instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return List.of();
    }
}