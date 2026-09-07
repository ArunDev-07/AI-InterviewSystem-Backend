package com.example.AI_InterviewSystem.agent.tools;

import com.example.AI_InterviewSystem.Service.GithubService;
import com.example.AI_InterviewSystem.agent.AgentRequestContext;
import com.example.AI_InterviewSystem.agent.ToolActivityRecorder;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Wraps GithubService — real HTTP calls to GitHub's public REST API. Unlike
 * ResumeRagTool/InterviewPerformanceTool, the GitHub username is deliberately
 * an LLM-supplied parameter, not read from AgentRequestContext: it's public
 * data about a GitHub account, not sensitive user-scoped data, so there's no
 * privacy reason to hide it from the model — the candidate's own resume
 * (ArunDev-07 etc.) IS the intended input here, same as the original spec's
 * example ("analyze my GitHub").
 *
 * Failures (bad username, rate limit, network issue) are caught here and
 * turned into a clear message for the agent rather than propagating — GitHub
 * is explicitly an OPTIONAL tool per the spec ("a failure of one optional
 * external tool should not necessarily crash the entire agent").
 *
 * FIX (2026-08-31): on long tool chains, MessageWindowChatMemory can evict the
 * message containing a prior successful analyzeGithub(realUsername) call
 * before the model needs that username again in the same turn — observed in
 * production as the model re-calling this with an empty string a few calls
 * later and never recovering. Rather than only rejecting that call, fall back
 * to AgentRequestContext.lastVerifiedGithubUsername() — a value THIS tool
 * itself already verified successfully earlier in this exact request — before
 * giving up. This never accepts an unverified value; it only re-uses one this
 * tool already confirmed real.
 */
@Component
public class GithubTool {

    private final GithubService githubService;

    public GithubTool(GithubService githubService) {
        this.githubService = githubService;
    }

    @Tool("""
        Analyze a candidate's public GitHub profile: their public repositories, primary
        programming languages used, and notable projects (by stars). Use this when the
        request asks about the candidate's GitHub, actual coding activity, or wants to
        verify resume claims against real projects. Requires a GitHub username (e.g.
        'ArunDev-07'), not a full URL. Do not call this unless the request is actually
        about GitHub — it's an optional check, not needed for every request.
        """)
    public String analyzeGithub(String githubUsername) {
        String preview = "githubUsername=" + githubUsername;
        return ToolActivityRecorder.track("analyzeGithub", preview, () -> {
            String effectiveUsername = githubUsername;

            if (looksLikePlaceholder(effectiveUsername)) {
                // Before giving up, check whether THIS tool already verified a real
                // username earlier in this same request — the model may simply have
                // lost track of it (e.g. it scrolled out of the chat memory window),
                // not be inventing a new one.
                String cached = AgentRequestContext.lastVerifiedGithubUsername();
                if (cached != null) {
                    effectiveUsername = cached;
                } else {
                    return "'" + githubUsername + "' looks like a placeholder, not a real GitHub " +
                            "username. Call searchResume to find the candidate's actual GitHub " +
                            "username (it's usually listed near their contact info), or ask the " +
                            "candidate for it directly — do not guess or invent one.";
                }
            }

            GithubService.GithubAnalysis analysis;
            try {
                analysis = githubService.analyzeUser(effectiveUsername);
            } catch (GithubService.GithubUserNotFoundException e) {
                return "No GitHub user found with username '" + effectiveUsername + "'. " +
                        "Double-check the username — it should be just the handle, not a full URL.";
            } catch (GithubService.GithubRateLimitException e) {
                return "GitHub API rate limit was hit, so GitHub analysis isn't available right now. " +
                        "Proceed without it and mention this limitation to the candidate.";
            } catch (Exception e) {
                return "GitHub analysis failed unexpectedly (" + e.getMessage() + "). " +
                        "Proceed without it and mention this limitation to the candidate.";
            }

            // Only cache AFTER a real, successful lookup — never cache an unverified value.
            AgentRequestContext.rememberGithubUsername(effectiveUsername);

            return formatAnalysis(analysis);
        });
    }

    /**
     * Catches the common shapes of invented/placeholder usernames a small model
     * tends to substitute when it doesn't have a real one: "yourGitHubUsername",
     * "your_username", "username", "githubusername", or anything containing a
     * space (real GitHub handles never contain spaces).
     */
    private boolean looksLikePlaceholder(String username) {
        if (username == null || username.isBlank()) return true;
        String normalized = username.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
        return normalized.contains("your")
                || normalized.equals("username")
                || normalized.equals("githubusername")
                || normalized.equals("example")
                || username.contains(" ");
    }

    private String formatAnalysis(GithubService.GithubAnalysis analysis) {
        StringBuilder sb = new StringBuilder();

        sb.append("GITHUB PROFILE: ").append(analysis.profile().username()).append("\n");
        if (analysis.profile().bio() != null && !analysis.profile().bio().isBlank()) {
            sb.append("Bio: ").append(analysis.profile().bio()).append("\n");
        }
        sb.append("Public repos: ").append(analysis.profile().publicRepos())
                .append(", Followers: ").append(analysis.profile().followers()).append("\n\n");

        if (analysis.languageCounts().isEmpty()) {
            sb.append("No language data available from public repos.\n");
        } else {
            sb.append("LANGUAGES ACROSS REPOS (repo count per language):\n");
            analysis.languageCounts().entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append(" repos\n"));
            sb.append("\n");
        }

        List<GithubService.GithubRepoSummary> topRepos = analysis.topRepos();
        if (topRepos.isEmpty()) {
            sb.append("No public repositories found (excluding forks).\n");
        } else {
            sb.append("NOTABLE REPOSITORIES:\n");
            for (GithubService.GithubRepoSummary repo : topRepos) {
                sb.append("- ").append(repo.name());
                if (repo.primaryLanguage() != null) {
                    sb.append(" [").append(repo.primaryLanguage()).append("]");
                }
                sb.append(", ").append(repo.stars()).append(" stars\n");
                if (repo.description() != null && !repo.description().isBlank()) {
                    sb.append("  ").append(repo.description()).append("\n");
                }
                if (!repo.topics().isEmpty()) {
                    sb.append("  topics: ").append(String.join(", ", repo.topics())).append("\n");
                }
            }
        }

        return sb.toString();
    }
}