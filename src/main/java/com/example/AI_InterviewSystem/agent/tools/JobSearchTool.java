package com.example.AI_InterviewSystem.agent.tools;

import com.example.AI_InterviewSystem.Service.JobSearchService;
import com.example.AI_InterviewSystem.agent.ToolActivityRecorder;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Wraps JobSearchService — real HTTP calls to Adzuna's live job listings API.
 * Optional tool, same failure-isolation pattern as GithubTool: a missing API
 * key or rate limit doesn't crash the agent, it returns a clear message the
 * model can work around.
 */
@Component
public class JobSearchTool {

    private static final int MAX_LISTINGS_SHOWN = 5;

    private final JobSearchService jobSearchService;

    public JobSearchTool(JobSearchService jobSearchService) {
        this.jobSearchService = jobSearchService;
    }

    @Tool("""
        Search for current, real job listings matching a role/skills. Use this when
        the candidate asks to find jobs, check what's currently hiring, or wants to
        see real openings matching their profile. 'what' should be a short search
        query like 'Java Spring Boot developer' — combine role and 1-3 key skills,
        don't paste a whole skill list. 'where' is an optional city/location (e.g.
        'Coimbatore', 'Bangalore') — omit it to search nationally. This searches
        India job listings. Do not call this unless the request is actually about
        finding jobs — it's optional, not needed for resume/prep-plan requests.
        """)
    public String searchJobs(String what, String where) {
        String preview = "what=" + what + " | where=" + where;
        return ToolActivityRecorder.track("searchJobs", preview, () -> {
            List<JobSearchService.JobListing> listings;
            try {
                listings = jobSearchService.searchJobs(what, where);
            } catch (JobSearchService.AdzunaNotConfiguredException e) {
                return "Job search isn't configured (missing Adzuna API credentials). " +
                        "Proceed without it and mention this limitation to the candidate.";
            } catch (JobSearchService.AdzunaRateLimitException e) {
                return "Job search API rate limit was hit, so live job listings aren't " +
                        "available right now. Proceed without it and mention this limitation.";
            } catch (Exception e) {
                return "Job search failed unexpectedly (" + e.getMessage() + "). " +
                        "Proceed without it and mention this limitation to the candidate.";
            }

            if (listings.isEmpty()) {
                return "No current job listings found for '" + what + "'" +
                        (where != null && !where.isBlank() ? " in " + where : "") + ".";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("CURRENT JOB LISTINGS for '").append(what).append("'");
            if (where != null && !where.isBlank()) sb.append(" in ").append(where);
            sb.append(":\n\n");

            int shown = 0;
            for (JobSearchService.JobListing job : listings) {
                if (shown >= MAX_LISTINGS_SHOWN) break;
                sb.append("- ").append(job.title() == null ? "Untitled role" : job.title());
                if (job.companyName() != null) sb.append(" at ").append(job.companyName());
                if (job.location() != null) sb.append(" (").append(job.location()).append(")");
                sb.append("\n");
                if (job.salaryMin() != null && job.salaryMax() != null) {
                    sb.append("  salary range: ").append(Math.round(job.salaryMin()))
                            .append(" - ").append(Math.round(job.salaryMax())).append("\n");
                }
                if (job.redirectUrl() != null) {
                    sb.append("  link: ").append(job.redirectUrl()).append("\n");
                }
                shown++;
            }

            if (listings.size() > MAX_LISTINGS_SHOWN) {
                sb.append("\n(").append(listings.size() - MAX_LISTINGS_SHOWN).append(" more results not shown)");
            }

            return sb.toString();
        });
    }
}