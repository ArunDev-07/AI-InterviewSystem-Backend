package com.example.AI_InterviewSystem.agent.tools;

import com.example.AI_InterviewSystem.agent.ToolActivityRecorder;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Synthesizes skill-gap data + past performance + job requirements into a
 * structured, prioritized artifact for the agent to turn into its final
 * roadmap. Deliberately does NOT call an LLM (single-LLM architecture) — it's
 * deterministic cross-referencing, same category as JobDescriptionTool/
 * SkillGapTool. No service layer needed: no I/O, no state, nothing to reuse
 * or unit-test independently of this class.
 *
 * The actual "roadmap prose" / "mock interview recommendations" the original
 * spec asks for is produced by the agent's own reasoning AFTER calling this
 * tool — this tool's job is to hand it well-organized, cross-referenced raw
 * material instead of three unrelated text blobs, and to do the one piece of
 * real logic that benefits from code rather than LLM guessing: matching
 * "missing skill" strings against "weak feedback" text so the agent doesn't
 * have to eyeball that itself.
 */
@Component
public class PreparationPlannerTool {

    @Tool("""
        Build a prioritized preparation plan by combining skill gap analysis, past
        interview performance, and job requirements. Call this AFTER you have called
        analyzeSkillGap (or otherwise know the missing skills) and, if the candidate
        has interview history, getInterviewPerformance. Pass whatever you have — this
        tool works with partial input, e.g. only skillGaps if there's no performance
        history yet. Use the result to write the final roadmap for the candidate;
        don't just repeat this tool's output verbatim.
        """)
    public String createPreparationPlan(String skillGapsSummary, String performanceSummary, String jobRequirementsSummary) {
        String preview = "skillGaps=" + skillGapsSummary + " | performance=" + performanceSummary + " | jobRequirements=" + jobRequirementsSummary;
        return ToolActivityRecorder.track("createPreparationPlan", preview, () -> {
            boolean hasSkillGaps = notBlank(skillGapsSummary);
            boolean hasPerformance = notBlank(performanceSummary);
            boolean hasJobRequirements = notBlank(jobRequirementsSummary);

            if (!hasSkillGaps && !hasPerformance && !hasJobRequirements) {
                return "No skill gap, performance, or job requirement data was provided — " +
                        "call searchResume / analyzeJobDescription / analyzeSkillGap / " +
                        "getInterviewPerformance first to gather something to plan around.";
            }

            List<String> missingSkills = extractMissingSkills(skillGapsSummary);
            Set<String> weakInFeedback = crossReferenceWeakAreas(missingSkills, performanceSummary);

            StringBuilder sb = new StringBuilder();
            sb.append("PLANNING INPUT SUMMARY\n");
            sb.append("- Skill gap data provided: ").append(hasSkillGaps).append("\n");
            sb.append("- Performance history provided: ").append(hasPerformance).append("\n");
            sb.append("- Job requirements provided: ").append(hasJobRequirements).append("\n\n");

            if (!missingSkills.isEmpty()) {
                sb.append("PRIORITY RANKING (highest priority first):\n");

                List<String> doublePriority = new ArrayList<>();
                List<String> singlePriority = new ArrayList<>();
                for (String skill : missingSkills) {
                    if (weakInFeedback.contains(skill.toLowerCase(Locale.ROOT))) {
                        doublePriority.add(skill);
                    } else {
                        singlePriority.add(skill);
                    }
                }

                int rank = 1;
                for (String skill : doublePriority) {
                    sb.append(rank++).append(". ").append(skill)
                            .append("  [HIGH — missing from resume AND flagged in past feedback]\n");
                }
                for (String skill : singlePriority) {
                    sb.append(rank++).append(". ").append(skill)
                            .append("  [missing from resume, no matching prior feedback found]\n");
                }
                sb.append("\n");
            } else {
                sb.append("No specific missing skills were identified to rank — ")
                        .append("base the plan on general readiness from performance feedback instead.\n\n");
            }

            sb.append("RAW SKILL GAP DATA:\n").append(hasSkillGaps ? skillGapsSummary : "(none provided)").append("\n\n");
            sb.append("RAW PERFORMANCE DATA:\n").append(hasPerformance ? performanceSummary : "(none provided)").append("\n\n");
            sb.append("RAW JOB REQUIREMENTS:\n").append(hasJobRequirements ? jobRequirementsSummary : "(none provided)").append("\n\n");

            sb.append("INSTRUCTIONS FOR YOU (the agent): using the above, write the candidate a ")
                    .append("concrete preparation sequence (what to study first, in what order), ")
                    .append("2-3 specific practice question topics per high-priority area, and whether ")
                    .append("a mock interview round in this app (Aptitude/Communication/DSA/HR) would help ")
                    .append("verify readiness on each area. Be specific, not generic.");

            return sb.toString();
        });
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Parses the "MISSING SKILLS (priority to prepare): x, y, z" line that
     * SkillGapTool.analyzeSkillGap() produces. Falls back to an empty list if
     * the summary doesn't match that shape (e.g. agent passed something else in).
     */
    private List<String> extractMissingSkills(String skillGapsSummary) {
        if (!notBlank(skillGapsSummary)) return List.of();

        for (String line : skillGapsSummary.split("\n")) {
            if (line.startsWith("MISSING SKILLS")) {
                String afterColon = line.substring(line.indexOf(':') + 1).trim();
                if (afterColon.equalsIgnoreCase("none — full coverage") || afterColon.equalsIgnoreCase("none")) {
                    return List.of();
                }
                List<String> skills = new ArrayList<>();
                for (String s : afterColon.split(",")) {
                    String trimmed = s.trim();
                    if (!trimmed.isEmpty()) skills.add(trimmed);
                }
                return skills;
            }
        }
        return List.of();
    }

    /** Which missing skills also show up (as plain substring match) in the performance feedback text. */
    private Set<String> crossReferenceWeakAreas(List<String> missingSkills, String performanceSummary) {
        if (missingSkills.isEmpty() || !notBlank(performanceSummary)) return Set.of();

        String feedbackLower = performanceSummary.toLowerCase(Locale.ROOT);
        Set<String> hits = new HashSet<>();
        for (String skill : missingSkills) {
            if (feedbackLower.contains(skill.toLowerCase(Locale.ROOT))) {
                hits.add(skill.toLowerCase(Locale.ROOT));
            }
        }
        return hits;
    }
}