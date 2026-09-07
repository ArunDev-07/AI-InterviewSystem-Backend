package com.example.AI_InterviewSystem.agent.tools;

import com.example.AI_InterviewSystem.Model.CandidateProfile;
import com.example.AI_InterviewSystem.Service.CandidateProfileService;
import com.example.AI_InterviewSystem.Service.CompanyRecommendationService;
import com.example.AI_InterviewSystem.Service.JobSearchService;
import com.example.AI_InterviewSystem.agent.AgentRequestContext;
import com.example.AI_InterviewSystem.agent.ToolActivityRecorder;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Answers "which companies/jobs suit ME" — candidate-to-company FIT — as
 * opposed to CompanyResearchTool, which answers "tell me about [a named
 * company]".
 *
 * DESIGN NOTE: this tool takes NO arguments. It resolves the candidate's
 * profile itself via AgentRequestContext.username(), rather than asking the
 * LLM to pass profile data (role/skills/etc.) in as parameters. Two reasons:
 *   1. Security/anti-hallucination — the model can't (even accidentally)
 *      substitute a different or partially-remembered profile into the match;
 *      the match is always run against the actual persisted CandidateProfile.
 *   2. Reliability — small local models are inconsistent about faithfully
 *      copying a large block of prior tool output into a new tool call's
 *      arguments. Reading the profile directly removes that failure mode.
 * The system prompt still tells the agent to "retrieve the candidate profile"
 * conceptually before recommending companies — this tool satisfies that by
 * doing the retrieval itself, deterministically, every time it runs.
 */
@Component
public class CompanyRecommendationTool {

    private final CandidateProfileService candidateProfileService;
    private final CompanyRecommendationService companyRecommendationService;

    public CompanyRecommendationTool(
            CandidateProfileService candidateProfileService,
            CompanyRecommendationService companyRecommendationService
    ) {
        this.candidateProfileService = candidateProfileService;
        this.companyRecommendationService = companyRecommendationService;
    }

    @Tool("""
        Recommend which COMPANIES OR JOBS actually suit this candidate, matching their
        saved profile against CURRENT real job listings. Use this ONLY for
        candidate-to-company/job FIT questions — e.g. "which companies suit me",
        "which companies should I target", "what companies are a good fit for my
        profile". Do NOT use this for a specific named company someone asked about
        (use researchCompany for that instead), and do NOT name companies from your
        own general knowledge (Google, Amazon, Microsoft, Zoho, etc.) for a fit
        question — this tool is the only source of truth for that. It reads the
        candidate's saved profile itself; you don't pass anything in. If no profile
        exists yet, it will tell you so — analyze the candidate (searchResume /
        analyzeGithub), call saveCandidateProfile, then retry.
        """)
    public String recommendCompanies() {
        return ToolActivityRecorder.track("recommendCompanies", () -> {
            String username = AgentRequestContext.username();
            CandidateProfile profile = candidateProfileService.getProfile(username).orElse(null);

            if (profile == null) {
                return "No saved candidate profile found for this user yet. Call searchResume " +
                        "(and analyzeGithub if you have a real, verified GitHub username) to " +
                        "analyze the candidate, determine a recommended role, then call " +
                        "saveCandidateProfile before recommending companies. Do not name specific " +
                        "companies without doing this first.";
            }

            List<CompanyRecommendationService.CompanyMatch> matches;
            try {
                matches = companyRecommendationService.recommendCompanies(profile);
            } catch (CompanyRecommendationService.NoCandidateDataException e) {
                return "The saved candidate profile doesn't have enough information to match " +
                        "against real listings yet: " + e.getMessage() + " Analyze the resume/" +
                        "GitHub further, call saveCandidateProfile with what you find, then retry.";
            } catch (JobSearchService.AdzunaNotConfiguredException e) {
                return "Company recommendation needs live job search, which isn't configured " +
                        "(missing Adzuna API credentials). Tell the candidate this data isn't " +
                        "available right now rather than substituting a guessed list of companies.";
            } catch (JobSearchService.AdzunaRateLimitException e) {
                return "The job search API's rate limit was hit, so live company matching isn't " +
                        "available right now. Tell the candidate this rather than substituting a " +
                        "guessed list of companies.";
            } catch (Exception e) {
                return "Company recommendation failed unexpectedly (" + e.getMessage() + "). " +
                        "Tell the candidate this data isn't available right now rather than " +
                        "substituting a guessed list of companies.";
            }

            if (matches.isEmpty()) {
                return "No current real job listings matched this candidate's profile (role: " +
                        nullToNone(profile.getRecommendedRole()) + ", skills: " +
                        nullToNone(profile.getSkills()) + "). Tell the candidate no strong company " +
                        "match was found in current listings — do not name companies from general " +
                        "knowledge instead.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("CANDIDATE-TO-COMPANY MATCHES (from current real job listings, ranked by evidence):\n\n");
            int rank = 1;
            for (CompanyRecommendationService.CompanyMatch m : matches) {
                sb.append(rank++).append(". ").append(m.companyName()).append(" — ").append(m.jobTitle());
                if (m.location() != null && !m.location().isBlank()) {
                    sb.append(" (").append(m.location()).append(")");
                }
                sb.append("\n   matched evidence: ")
                        .append(m.matchedSkills().isEmpty() ? "role title match only" : String.join(", ", m.matchedSkills()));
                if (m.redirectUrl() != null && !m.redirectUrl().isBlank()) {
                    sb.append("\n   listing: ").append(m.redirectUrl());
                }
                sb.append("\n");
            }
            sb.append("\nExplain WHY each company fits using ONLY the matched evidence above — " +
                    "do not add companies or reasons not shown here.");

            return sb.toString();
        });
    }

    private String nullToNone(String s) {
        return (s == null || s.isBlank()) ? "none recorded" : s;
    }
}