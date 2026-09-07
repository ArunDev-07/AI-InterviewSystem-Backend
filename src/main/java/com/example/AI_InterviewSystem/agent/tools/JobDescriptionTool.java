package com.example.AI_InterviewSystem.agent.tools;

import com.example.AI_InterviewSystem.agent.ToolActivityRecorder;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts structure from a raw job description before the agent's Ollama brain
 * reasons over it. Deliberately does NOT call an LLM itself — the spec requires
 * exactly one LLM in this system (the agent's). This tool does cheap, deterministic
 * preprocessing (skill/tech keyword spotting + section splitting) so the agent has
 * a clean, structured artifact to reason on top of, instead of a wall of text.
 */
@Component
public class JobDescriptionTool {

    // Real job descriptions are always substantial blocks of text with multiple
    // requirement/responsibility lines. A short phrase like "I am applying for a
    // backend developer position" or "for a backend role" is the candidate
    // describing what THEY want, not a job posting — but a weaker model can
    // still mistake the former for the latter (fabricating a "job description"
    // out of the candidate's own goal instead of calling searchResume). This
    // floor catches that deterministically, regardless of what the system
    // prompt says, since prompt instructions alone aren't reliably followed by
    // smaller local models.
    private static final int MIN_PLAUSIBLE_JD_LENGTH = 100;

    private static final Pattern EXPERIENCE_PATTERN =
            Pattern.compile("(\\d+)\\s*(?:\\+)?\\s*(?:to\\s*(\\d+))?\\s*years?", Pattern.CASE_INSENSITIVE);

    @Tool("""
        Analyze a raw job description and extract structured information: required
        technologies/skills found in it, likely experience requirement, and the
        responsibility/requirement sentences. Call this once per job description before
        reasoning about skill gaps or fit. Pass the full job description text. Only call
        this with an ACTUAL job posting (multiple sentences of real requirements/
        responsibilities) — do not call this with a short phrase describing what job
        the candidate wants (e.g. "backend developer role"); that is not a job
        description and this tool will reject it.
        """)
    public String analyzeJobDescription(String jobDescription) {
        String preview = "jobDescription=" + jobDescription;
        return ToolActivityRecorder.track("analyzeJobDescription", preview, () -> {
            if (jobDescription == null || jobDescription.isBlank()) {
                return "No job description text was provided.";
            }

            String trimmed = jobDescription.trim();
            if (trimmed.length() < MIN_PLAUSIBLE_JD_LENGTH) {
                return "This does not look like an actual job description — it's too short " +
                        "(\"" + trimmed + "\"). A real job description is a substantial block of " +
                        "text with multiple requirement/responsibility lines, not a short phrase. " +
                        "If the candidate actually pasted or referenced a real job posting, ask " +
                        "them to share the full text of it. If this request is instead about the " +
                        "CANDIDATE'S OWN skills, background, or readiness (e.g. they only named a " +
                        "target role or job title, not a real posting), do NOT treat this as a job " +
                        "description — call searchResume instead to answer based on their actual " +
                        "resume.";
            }

            List<String> foundSkills = new ArrayList<>();
            for (String keyword : TechKeywords.KNOWN_TECH_KEYWORDS) {
                if (containsWord(jobDescription, keyword)) {
                    foundSkills.add(keyword);
                }
            }

            String experience = extractExperience(jobDescription);
            List<String> responsibilityLines = extractBulletOrSentenceLines(jobDescription);

            StringBuilder sb = new StringBuilder();
            sb.append("DETECTED SKILLS/TECHNOLOGIES: ")
                    .append(foundSkills.isEmpty() ? "none confidently detected" : String.join(", ", foundSkills))
                    .append("\n");
            sb.append("EXPERIENCE REQUIREMENT: ").append(experience).append("\n");
            sb.append("KEY LINES (responsibilities/requirements, for the agent to interpret):\n");
            for (String line : responsibilityLines) {
                sb.append("- ").append(line).append("\n");
            }
            sb.append("\nNOTE TO AGENT: the skill list above is keyword-matched, not exhaustive. ")
                    .append("Read the key lines yourself to identify important interview topics, ")
                    .append("soft requirements, and anything the keyword list missed.");

            return sb.toString();
        });
    }

    private boolean containsWord(String text, String keyword) {
        String pattern = "(?i)\\b" + Pattern.quote(keyword) + "\\b";
        return Pattern.compile(pattern).matcher(text).find();
    }

    private String extractExperience(String text) {
        Matcher m = EXPERIENCE_PATTERN.matcher(text);
        if (m.find()) {
            String min = m.group(1);
            String max = m.group(2);
            return max != null ? (min + "-" + max + " years") : (min + "+ years");
        }
        return "not explicitly stated";
    }

    private List<String> extractBulletOrSentenceLines(String text) {
        String[] rawLines = text.split("\\r?\\n");
        List<String> lines = new ArrayList<>();
        for (String line : rawLines) {
            String trimmed = line.trim().replaceFirst("^[-*•]\\s*", "");
            if (trimmed.length() > 15) {
                lines.add(trimmed);
            }
        }
        if (lines.size() <= 1) {
            String[] sentences = text.split("(?<=[.!?])\\s+");
            lines.clear();
            for (String s : sentences) {
                String trimmed = s.trim();
                if (trimmed.length() > 15) {
                    lines.add(trimmed);
                }
            }
        }
        return lines.size() > 25 ? lines.subList(0, 25) : lines;
    }
}