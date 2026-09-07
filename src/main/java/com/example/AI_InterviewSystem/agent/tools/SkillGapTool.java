package com.example.AI_InterviewSystem.agent.tools;

import com.example.AI_InterviewSystem.agent.ToolActivityRecorder;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Compares candidate skills against required skills. Deterministic set logic —
 * no LLM call here by design (single-LLM architecture). The agent's Ollama brain
 * is responsible for deciding what candidateSkills/requiredSkills to pass in
 * (usually derived from prior searchResume + analyzeJobDescription tool calls)
 * and for interpreting the result afterward.
 */
@Component
public class SkillGapTool {

    @Tool("""
        Compare the candidate's skills against a job's required skills to find matches,
        gaps, and priority areas. Pass comma-separated skill lists. Call this AFTER you
        have both the candidate's skills (from searchResume) and the required skills
        (from analyzeJobDescription) — do not guess either list yourself.

        IMPORTANT: candidateSkills and requiredSkills must contain actual technical/
        professional skills and technologies ONLY (e.g. "Java, Spring Boot, Docker").
        Do NOT include education entries, degree names, institution names, or grades
        — those are not skills. Do NOT pass a GitHub username, a job title, or a
        role description (e.g. "backend developer role") as a skill list — those
        aren't skills either and this tool will reject them.
        """)
    public String analyzeSkillGap(String candidateSkills, String requiredSkills) {
        String preview = "candidateSkills=" + candidateSkills + " | requiredSkills=" + requiredSkills;
        return ToolActivityRecorder.track("analyzeSkillGap", preview, () -> {
            if (looksLikeUnresolvedPlaceholder(candidateSkills, "candidateSkills")
                    || looksLikeUnresolvedPlaceholder(requiredSkills, "requiredSkills")) {
                return "The values passed in ('" + candidateSkills + "' / '" + requiredSkills + "') look like " +
                        "placeholder text (e.g. the literal parameter name), not real skill lists — this call was " +
                        "NOT scored, since it would produce a meaningless comparison. You must have BOTH real " +
                        "data before calling this tool: call searchResume to get the candidate's actual skills, " +
                        "and analyzeJobDescription to get the actual required skills from a real job posting. " +
                        "Retry only once you have real comma-separated skill names from those tools' output.";
            }

            String nonSkillField = firstNonSkillLikeValue(candidateSkills, requiredSkills);
            if (nonSkillField != null) {
                return "'" + nonSkillField + "' does not look like a skill or skill list — it reads like a " +
                        "username, role/job title, or short phrase instead (e.g. 'ArunDev-07' or 'backend " +
                        "developer role' are NOT skills). This call was NOT scored. candidateSkills must come " +
                        "from searchResume and requiredSkills must come from analyzeJobDescription — never a " +
                        "role name, target job title, or GitHub username. Retry with real skill names.";
            }

            Set<String> candidate = normalize(candidateSkills);
            Set<String> required = normalize(requiredSkills);

            if (required.isEmpty()) {
                return "No required skills were provided to compare against.";
            }

            Set<String> matching = new LinkedHashSet<>(required);
            matching.retainAll(candidate);

            Set<String> missing = new LinkedHashSet<>(required);
            missing.removeAll(candidate);

            int total = required.size();
            int matched = matching.size();
            int matchPercent = total == 0 ? 0 : (int) Math.round((matched * 100.0) / total);

            StringBuilder sb = new StringBuilder();
            sb.append("MATCH: ").append(matchPercent).append("% (")
                    .append(matched).append("/").append(total).append(" required skills present)\n");
            sb.append("MATCHING SKILLS: ")
                    .append(matching.isEmpty() ? "none" : String.join(", ", matching)).append("\n");
            sb.append("MISSING SKILLS (priority to prepare): ")
                    .append(missing.isEmpty() ? "none — full coverage" : String.join(", ", missing)).append("\n");

            if (!missing.isEmpty()) {
                sb.append("SUGGESTED PRIORITY ORDER: ")
                        .append(String.join(" > ", missing))
                        .append(" (agent should re-rank this using interview performance data if available)");
            }

            return sb.toString();
        });
    }

    /**
     * Catches the shape seen in production: the model echoing the parameter
     * name itself back as the value (e.g. candidateSkills="candidateSkills"),
     * or other generic non-answers, instead of real comma-separated skills.
     */
    private boolean looksLikeUnresolvedPlaceholder(String csv, String paramName) {
        if (csv == null || csv.isBlank()) return false;
        String normalized = csv.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (normalized.isEmpty()) return false;
        if (normalized.equals(paramName.toLowerCase(Locale.ROOT))) return true;
        return normalized.equals("candidateskills")
                || normalized.equals("requiredskills")
                || normalized.equals("skills")
                || normalized.equals("skill")
                || normalized.equals("string")
                || normalized.equals("skill1skill2skill3");
    }

    /**
     * FIX (production incident 2026-08-31): analyzeSkillGap was called with
     * candidateSkills="ArunDev-07" (a GitHub username) and requiredSkills=
     * "backend developer role" (a job title) — neither is a skill list, and
     * looksLikeUnresolvedPlaceholder() didn't catch either shape.
     *
     * Heuristic: a real skill list either (a) has a comma — trust it, or (b)
     * is a single value that exactly matches a known skill/tech name (from
     * TechKeywords, e.g. "Java" alone is fine). A single comma-less value
     * that ISN'T a known skill name, contains a digit/hyphen (username-like:
     * "ArunDev-07"), or is multiple words (title-like: "backend developer
     * role") is almost certainly the wrong kind of input.
     *
     * Trade-off: a genuine but obscure single/multi-word skill not in
     * TechKeywords AND passed alone with no comma could be a false positive
     * (e.g. a candidate whose only listed skill is some niche two-word tool
     * name). Extend TechKeywords if that happens in practice rather than
     * loosening this check.
     *
     * Returns the first offending raw value, or null if both look fine.
     */
    private String firstNonSkillLikeValue(String candidateSkills, String requiredSkills) {
        if (looksLikeNonSkillValue(candidateSkills)) return candidateSkills;
        if (looksLikeNonSkillValue(requiredSkills)) return requiredSkills;
        return null;
    }

    private boolean looksLikeNonSkillValue(String csv) {
        if (csv == null || csv.isBlank()) return false;
        String trimmed = csv.trim();
        if (trimmed.contains(",")) return false; // real list — trust it

        for (String keyword : TechKeywords.KNOWN_TECH_KEYWORDS) {
            if (trimmed.equalsIgnoreCase(keyword)) return false;
        }

        boolean multiWord = trimmed.split("\\s+").length > 1;
        boolean handleLike = trimmed.matches(".*[0-9-].*");
        return multiWord || handleLike;
    }

    private Set<String> normalize(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String s : csv.split(",")) {
            String trimmed = s.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }
}