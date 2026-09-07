package com.example.AI_InterviewSystem.Model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persistent, structured "long-term memory" about a candidate — separate from the
 * agent's short-term MessageWindowChatMemory (which only holds the last few
 * messages of ONE conversation and does not survive an application restart).
 *
 * One row per username (enforced via a unique column, not per-session/per-interview)
 * so that facts the agent has already worked out about a candidate — recommended
 * role, skills, projects, experience, verified GitHub username — are available to
 * ANY future session for that same candidate, not just the one where they were
 * derived. interviewId is kept as informational context (which resume attempt this
 * was last derived from), not as part of the identity key.
 *
 * SECURITY: this entity carries no access-control logic itself — that lives in
 * CandidateProfileService/CandidateProfileTool, which only ever look this up by
 * AgentRequestContext.username() (never a value supplied by the LLM/user input).
 * Never add a repository method that looks this up by anything other than the
 * authenticated username.
 */
@Entity
@Table(name = "candidate_profiles")
public class CandidateProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    // Which interview/resume attempt this profile was most recently derived
    // from. Informational only — the profile survives independently of any
    // one interview or session.
    private Long interviewId;

    // The candidate's own real GitHub username, as verified via a successful
    // analyzeGithub tool call — never a value invented by the LLM. See the
    // placeholder guard in CandidateProfileTool.saveCandidateProfile().
    private String githubUsername;

    private String recommendedRole;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String skills;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String projects;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String experience;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public CandidateProfile() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Long getInterviewId() { return interviewId; }
    public void setInterviewId(Long interviewId) { this.interviewId = interviewId; }

    public String getGithubUsername() { return githubUsername; }
    public void setGithubUsername(String githubUsername) { this.githubUsername = githubUsername; }

    public String getRecommendedRole() { return recommendedRole; }
    public void setRecommendedRole(String recommendedRole) { this.recommendedRole = recommendedRole; }

    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }

    public String getProjects() { return projects; }
    public void setProjects(String projects) { this.projects = projects; }

    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}