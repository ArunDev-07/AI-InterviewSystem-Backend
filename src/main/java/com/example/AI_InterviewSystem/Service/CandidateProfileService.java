package com.example.AI_InterviewSystem.Service;

import com.example.AI_InterviewSystem.Model.CandidateProfile;
import com.example.AI_InterviewSystem.Repository.CandidateProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Owns all read/write access to CandidateProfile.
 *
 * SECURITY CONTRACT (mirrors your existing model — see AgentRequestContext,
 * InterviewPerformanceTool/InterviewService): every method here takes username
 * as an explicit parameter, and callers (currently only CandidateProfileTool /
 * CompanyRecommendationTool) MUST pass AgentRequestContext.username() — never a
 * value the LLM/user supplied directly as a tool argument. This class has no
 * way to enforce that from within itself (same as InterviewService today), so
 * it must never be called from anywhere that accepts a username from
 * untrusted input.
 *
 * Upsert-by-username avoids duplicate profiles per candidate: exactly one row
 * per username, updated over time as more is learned, never re-inserted.
 */
@Service
public class CandidateProfileService {

    private final CandidateProfileRepository repository;

    public CandidateProfileService(CandidateProfileRepository repository) {
        this.repository = repository;
    }

    public Optional<CandidateProfile> getProfile(String username) {
        return repository.findByUsername(username);
    }

    /**
     * Creates the candidate's profile if none exists yet, otherwise updates it
     * in place — never inserts a second row for the same username.
     *
     * Each field is only overwritten if a non-blank value is supplied. This
     * matters because the agent may call this again later with only a subset
     * of fields freshly re-derived (e.g. just an updated role after a new
     * skill-gap analysis) — a blank/omitted field here must NOT blow away a
     * previously recorded value for that field.
     */
    @Transactional
    public CandidateProfile saveOrUpdateProfile(
            String username,
            Long interviewId,
            String githubUsername,
            String recommendedRole,
            String skills,
            String projects,
            String experience
    ) {
        CandidateProfile profile = repository.findByUsername(username)
                .orElseGet(() -> {
                    CandidateProfile p = new CandidateProfile();
                    p.setUsername(username);
                    return p;
                });

        if (interviewId != null) {
            profile.setInterviewId(interviewId);
        }
        if (notBlank(githubUsername)) {
            profile.setGithubUsername(githubUsername.trim());
        }
        if (notBlank(recommendedRole)) {
            profile.setRecommendedRole(recommendedRole.trim());
        }
        if (notBlank(skills)) {
            profile.setSkills(skills.trim());
        }
        if (notBlank(projects)) {
            profile.setProjects(projects.trim());
        }
        if (notBlank(experience)) {
            profile.setExperience(experience.trim());
        }

        return repository.save(profile);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}