package com.example.AI_InterviewSystem.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobSearchService {

    private final RestClient adzunaRestClient;

    @Value("${adzuna.app-id:}")
    private String appId;

    @Value("${adzuna.app-key:}")
    private String appKey;

    // India by default — matches your target job market. Adzuna also supports
    // gb, us, and ~18 other country codes if this app is ever repurposed.
    private static final String DEFAULT_COUNTRY = "in";
    private static final int RESULTS_PER_PAGE = 10;

    public JobSearchService(RestClient adzunaRestClient) {
        this.adzunaRestClient = adzunaRestClient;
    }

    public static class AdzunaNotConfiguredException extends RuntimeException {
        public AdzunaNotConfiguredException() {
            super("Adzuna app-id/app-key are not configured — job search is unavailable.");
        }
    }

    public static class AdzunaRateLimitException extends RuntimeException {
        public AdzunaRateLimitException() {
            super("Adzuna API rate/quota limit reached. Try again later.");
        }
    }

    public record JobListing(
            String title,
            String companyName,
            String location,
            String description,
            Double salaryMin,
            Double salaryMax,
            String redirectUrl
    ) {}

    /**
     * Searches current job listings. role/skills go into Adzuna's free-text "what"
     * query; location is optional (Adzuna searches nationally within the country
     * if omitted). Country is fixed to India per DEFAULT_COUNTRY above — this app's
     * candidate base — not exposed as a parameter to keep the tool interface simple.
     */
    @SuppressWarnings("unchecked")
    public List<JobListing> searchJobs(String what, String where) {
        if (appId == null || appId.isBlank() || appKey == null || appKey.isBlank()) {
            throw new AdzunaNotConfiguredException();
        }

        if (what == null || what.isBlank()) {
            throw new IllegalArgumentException("Search query (what) cannot be empty");
        }

        String path = "/v1/api/jobs/{country}/search/1"
                + "?app_id={appId}&app_key={appKey}&results_per_page={perPage}"
                + "&what={what}"
                + (where != null && !where.isBlank() ? "&where={where}" : "")
                + "&content-type=application/json";

        Map<String, Object> response;
        try {
            if (where != null && !where.isBlank()) {
                response = adzunaRestClient.get()
                        .uri(path, DEFAULT_COUNTRY, appId, appKey, RESULTS_PER_PAGE, what, where)
                        .retrieve()
                        .body(Map.class);
            } else {
                response = adzunaRestClient.get()
                        .uri(path, DEFAULT_COUNTRY, appId, appKey, RESULTS_PER_PAGE, what)
                        .retrieve()
                        .body(Map.class);
            }
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new AdzunaRateLimitException();
        }

        if (response == null || response.get("results") == null) {
            return List.of();
        }

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");

        return results.stream()
                .map(this::toJobListing)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private JobListing toJobListing(Map<String, Object> job) {
        String title = stringOrNull(job.get("title"));

        String company = null;
        if (job.get("company") instanceof Map<?, ?> companyMap) {
            company = stringOrNull(companyMap.get("display_name"));
        }

        String location = null;
        if (job.get("location") instanceof Map<?, ?> locationMap) {
            location = stringOrNull(locationMap.get("display_name"));
        }

        String description = stringOrNull(job.get("description"));
        Double salaryMin = doubleOrNull(job.get("salary_min"));
        Double salaryMax = doubleOrNull(job.get("salary_max"));
        String redirectUrl = stringOrNull(job.get("redirect_url"));

        return new JobListing(title, company, location, description, salaryMin, salaryMax, redirectUrl);
    }

    private String stringOrNull(Object o) {
        return o == null ? null : o.toString();
    }

    private Double doubleOrNull(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return null;
    }
}