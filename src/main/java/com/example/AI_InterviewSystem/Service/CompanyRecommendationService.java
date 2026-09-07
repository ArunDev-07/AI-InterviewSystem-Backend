package com.example.AI_InterviewSystem.Service;

import com.example.AI_InterviewSystem.Model.CandidateProfile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Candidate-to-company/job MATCHING — deliberately distinct from
 * CompanyResearchService (which answers "tell me about a NAMED company" via
 * web search) and from JobSearchService used directly (which just lists
 * current openings for a query, without judging fit).
 *
 * LIMITATION (documented, not hidden): JobSearchService/JobListing as used by
 * JobSearchTool only exposes title, companyName, location, salary range, and
 * a redirect URL — no full job-description text. So matching is necessarily
 * against listing TITLES only, not full requirement text. This is a real
 * constraint of the existing integration, not a shortcut taken here — every
 * match is still backed by an ACTUAL current listing and an ACTUAL skill the
 * candidate has, never fabricated.
 *
 * FIX (2026-08-31) — production incident: a candidate with a Java/Spring
 * Boot/React skill set was recommended "HR Apprentice", "Infrastructure
 * Specialist" (x2), and "Windows Intune Engineer" — none sharing any real
 * skill overlap. Two contributing bugs, both fixed here:
 *
 *   1. Skill/role matching against the title used plain String.contains(),
 *      not word-boundary matching — unlike JobDescriptionTool.containsWord()
 *      elsewhere in this codebase, which correctly uses \b regex boundaries
 *      for the same kind of keyword spotting. Fixed to match that pattern.
 *
 *   2. A listing could survive (score > 0) on a ROLE-WORD match ALONE, with
 *      ZERO real skill overlap — and the tool's own display code labels that
 *      case "role title match only", which reads to the end user like
 *      legitimate evidence rather than the weak signal it is. Generic role
 *      words ("developer", "engineer", "specialist") appear across huge
 *      swaths of unrelated job titles, so a role-word-only match is close to
 *      noise. Fixed: a listing must now have at least one REAL matched skill
 *      to be included at all. The role-word boost still exists, but only as
 *      a tie-breaker ON TOP of a real skill match, never as a standalone
 *      qualifier.
 *
 *   Trade-off: if a candidate's saved profile has no skills recorded (only a
 *   role), this will now correctly return NO matches rather than guessing
 *   from the role alone — which matches the existing "don't fabricate,
 *   plainly say no match" pattern already used elsewhere in this tool
 *   (matches.isEmpty() case in CompanyRecommendationTool).
 */
@Service
public class CompanyRecommendationService {

    private static final int MAX_LISTINGS_TO_SCAN = 20;
    private static final int MAX_RECOMMENDATIONS = 5;
    private static final int MAX_SKILLS_IN_QUERY = 3;

    private final JobSearchService jobSearchService;

    public CompanyRecommendationService(JobSearchService jobSearchService) {
        this.jobSearchService = jobSearchService;
    }

    public record CompanyMatch(
            String companyName,
            String jobTitle,
            String location,
            List<String> matchedSkills,
            int matchScore,
            String redirectUrl
    ) {}

    /** Thrown when the candidate's saved profile doesn't have enough to match against. */
    public static class NoCandidateDataException extends RuntimeException {
        public NoCandidateDataException(String message) {
            super(message);
        }
    }

    /**
     * @throws NoCandidateDataException if the profile has no role/skills to match against
     * @throws JobSearchService.AdzunaNotConfiguredException propagated as-is from JobSearchService
     * @throws JobSearchService.AdzunaRateLimitException propagated as-is from JobSearchService
     */
    public List<CompanyMatch> recommendCompanies(CandidateProfile profile) {
        if (profile == null) {
            throw new NoCandidateDataException("No candidate profile is available to match against.");
        }

        List<String> skills = splitCsv(profile.getSkills());
        String role = profile.getRecommendedRole();

        if (skills.isEmpty() && (role == null || role.isBlank())) {
            throw new NoCandidateDataException(
                    "The saved candidate profile has no recorded skills or recommended role yet.");
        }

        // FIX: skills are now REQUIRED for any match (see class javadoc) — a role
        // alone is not enough evidence, even though it's still allowed to reach
        // this point so a clear "no skills recorded" message can be shown below
        // rather than a generic exception, matching this method's existing style.
        if (skills.isEmpty()) {
            return List.of();
        }

        String query = buildQuery(role, skills);

        // Reuses the SAME real Adzuna-backed service JobSearchTool already calls —
        // no new external integration, no invented data source.
        List<JobSearchService.JobListing> listings = jobSearchService.searchJobs(query, null);
        if (listings == null || listings.isEmpty()) {
            return List.of();
        }

        List<CompanyMatch> scored = new ArrayList<>();
        int scanned = 0;
        for (JobSearchService.JobListing listing : listings) {
            if (scanned >= MAX_LISTINGS_TO_SCAN) break;
            scanned++;

            CompanyMatch match = scoreListing(listing, skills, role);
            if (match != null) {
                scored.add(match);
            }
        }

        // Rank by strongest evidence first; keep only the best-evidenced listing
        // per company so the same employer doesn't crowd out the list with
        // several near-duplicate postings.
        Map<String, CompanyMatch> bestPerCompany = new LinkedHashMap<>();
        scored.stream()
                .sorted(Comparator.comparingInt(CompanyMatch::matchScore).reversed())
                .forEach(m -> bestPerCompany.putIfAbsent(m.companyName(), m));

        return bestPerCompany.values().stream()
                .limit(MAX_RECOMMENDATIONS)
                .collect(Collectors.toList());
    }

    private CompanyMatch scoreListing(JobSearchService.JobListing listing, List<String> skills, String role) {
        if (listing.companyName() == null || listing.companyName().isBlank()
                || listing.title() == null || listing.title().isBlank()) {
            return null;
        }

        String title = listing.title();

        List<String> matchedSkills = skills.stream()
                .filter(skill -> containsWord(title, skill))
                .collect(Collectors.toList());

        // FIX: a listing MUST have at least one real, word-boundary-matched skill
        // to be included at all — a role-word match alone is no longer sufficient
        // evidence (see class javadoc for the production incident this fixes).
        if (matchedSkills.isEmpty()) {
            return null;
        }

        int score = matchedSkills.size();

        // Role-word match is now only a TIE-BREAKER boost on top of a real skill
        // match, never a standalone qualifier.
        if (role != null && !role.isBlank()) {
            for (String roleWord : role.split("\\s+")) {
                if (roleWord.length() > 2 && containsWord(title, roleWord)) {
                    score++;
                    break; // one role-match boost, not one per word
                }
            }
        }

        return new CompanyMatch(
                listing.companyName(),
                listing.title(),
                listing.location(),
                matchedSkills,
                score,
                listing.redirectUrl()
        );
    }

    /**
     * Word-boundary, case-insensitive containment check — matches the pattern
     * already used by JobDescriptionTool.containsWord() elsewhere in this
     * codebase. Replaces the previous plain String.contains() check, which
     * could match a skill/role word as a substring inside an unrelated word
     * (e.g. matching inside "Infrastructure" or "Windows").
     */
    private boolean containsWord(String text, String word) {
        if (text == null || word == null || word.isBlank()) return false;
        String pattern = "(?i)\\b" + Pattern.quote(word.trim()) + "\\b";
        return Pattern.compile(pattern).matcher(text).find();
    }

    private String buildQuery(String role, List<String> skills) {
        StringBuilder sb = new StringBuilder();
        if (role != null && !role.isBlank()) {
            sb.append(role);
        }
        int added = 0;
        for (String skill : skills) {
            if (added >= MAX_SKILLS_IN_QUERY) break;
            if (sb.length() > 0) sb.append(" ");
            sb.append(skill);
            added++;
        }
        return sb.length() == 0 ? "software developer" : sb.toString();
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : csv.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }
}