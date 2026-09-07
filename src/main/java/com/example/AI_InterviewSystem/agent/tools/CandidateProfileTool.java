package com.example.AI_InterviewSystem.agent.tools;

import com.example.AI_InterviewSystem.Model.CandidateProfile;
import com.example.AI_InterviewSystem.Service.CandidateProfileService;
import com.example.AI_InterviewSystem.agent.AgentRequestContext;
import com.example.AI_InterviewSystem.agent.ToolActivityRecorder;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Gives the agent long-term, cross-session memory about the candidate, backed
 * by MySQL (CandidateProfile) — distinct from the short-lived, in-process
 * MessageWindowChatMemory that only covers the last few messages of ONE
 * conversation and does not survive a restart.
 *
 * SECURITY: neither method here takes a username parameter the model could
 * fill in. Both always resolve the current candidate via
 * AgentRequestContext.username() — set by AgentService from the authenticated
 * request, never from model/tool input — so this tool can never be pointed at
 * another candidate's profile.
 */
@Component
public class CandidateProfileTool {

    private final CandidateProfileService candidateProfileService;

    public CandidateProfileTool(CandidateProfileService candidateProfileService) {
        this.candidateProfileService = candidateProfileService;
    }

    @Tool("""
        Retrieve this candidate's previously saved profile: recommended role, skills,
        projects, experience, and verified GitHub username. This is LONG-TERM data —
        it may have been saved in a PREVIOUS session, and it survives even if this
        conversation's recent context does not (e.g. after an application restart).
        Call this for any follow-up that depends on something already determined
        about the candidate — e.g. "which company suits me", "why", "what should I
        prepare", or "what role did you recommend for me" — instead of relying on
        your own memory of this chat or re-deriving it from scratch. Takes no
        arguments; it always retrieves the current authenticated candidate's own
        profile, never anyone else's.
        """)
    public String getCandidateProfile() {
        return ToolActivityRecorder.track("getCandidateProfile", () -> {
            String username = AgentRequestContext.username();
            Optional<CandidateProfile> profileOpt = candidateProfileService.getProfile(username);

            if (profileOpt.isEmpty()) {
                return "No saved profile exists yet for this candidate. Analyze their resume " +
                        "(via searchResume) and, if you have a real verified GitHub username for " +
                        "them, analyzeGithub — then call saveCandidateProfile once you've " +
                        "determined a recommended role.";
            }

            CandidateProfile p = profileOpt.get();
            StringBuilder sb = new StringBuilder();
            sb.append("SAVED CANDIDATE PROFILE (long-term, from this or an earlier session):\n");
            sb.append("Recommended role: ").append(nullToNone(p.getRecommendedRole())).append("\n");
            sb.append("GitHub username: ").append(nullToNone(p.getGithubUsername())).append("\n");
            sb.append("Skills: ").append(nullToNone(p.getSkills())).append("\n");
            sb.append("Projects: ").append(nullToNone(p.getProjects())).append("\n");
            sb.append("Experience: ").append(nullToNone(p.getExperience())).append("\n");
            sb.append("Last updated: ").append(p.getUpdatedAt()).append("\n");
            return sb.toString();
        });
    }

    @Tool("""
        Save or update this candidate's long-term profile after you have ACTUALLY
        determined these facts from real tool output earlier in this conversation
        (searchResume, analyzeGithub, analyzeSkillGap) — never invent or guess any
        value. Call this once you've concluded a recommended/strongest role for the
        candidate, so later questions — in this session or a future one — can build
        on it without re-analyzing from scratch.

        Pass an empty string for any field you don't actually have evidence for —
        do NOT guess. Fields you leave empty keep whatever was previously saved;
        they are not erased.

        githubUsername MUST be the exact username you already used in a real,
        successful analyzeGithub call in this conversation — never a placeholder or
        invented handle. If you don't have one, pass an empty string.
        """)
    public String saveCandidateProfile(
            String recommendedRole,
            String githubUsername,
            String skills,
            String projects,
            String experience
    ) {
        String preview = "role=" + recommendedRole + " | github=" + githubUsername + " | skills=" + skills;
        return ToolActivityRecorder.track("saveCandidateProfile", preview, () -> {
            if (looksLikePlaceholderGithub(githubUsername)) {
                return "'" + githubUsername + "' looks like a placeholder GitHub username, not a " +
                        "real one you verified with analyzeGithub in this conversation — the " +
                        "profile was NOT saved with that value. Retry with the real username, or " +
                        "an empty string if you don't have one.";
            }

            String username = AgentRequestContext.username();
            Long interviewId = AgentRequestContext.interviewId();

            candidateProfileService.saveOrUpdateProfile(
                    username, interviewId, githubUsername, recommendedRole, skills, projects, experience
            );

            return "Candidate profile saved. It will be available for follow-up questions in " +
                    "this session and in future sessions.";
        });
    }

    /**
     * Same placeholder-detection approach as GithubTool.looksLikePlaceholder() —
     * a second line of defense here, since this tool records the username into
     * persistent storage rather than just using it for one lookup.
     */
    private boolean looksLikePlaceholderGithub(String username) {
        if (username == null || username.isBlank()) return false; // blank = "not provided" — fine
        String normalized = username.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return normalized.contains("your")
                || normalized.equals("username")
                || normalized.equals("githubusername")
                || normalized.equals("example")
                || username.contains(" ");
    }

    private String nullToNone(String s) {
        return (s == null || s.isBlank()) ? "none recorded" : s;
    }
}