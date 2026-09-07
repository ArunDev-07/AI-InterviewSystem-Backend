package com.example.AI_InterviewSystem.Model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persistent state for one Agentic AI preparation session. MySQL only — no Redis.
 *
 * Keyed by username (String), matching how the rest of your app resolves the
 * current user (UserRepo#findByUsername), rather than a numeric userId. Also
 * carries interviewId, since your resume RAG (PdfEmbeddingService) is scoped
 * per interview attempt, not just per user — the agent needs to know which
 * interview's resume chunks to search.
 */
@Entity
@Table(name = "agent_sessions")
public class AgentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    // Which Interview's resume/context this session is scoped to.
    // Defaults to the user's most recent interview if not explicitly provided —
    // see AgentService.resolveInterviewId().
    @Column(nullable = true)
    private Long interviewId;

    private String targetRole;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String skillGapsJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String completedTopicsJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String pendingTopicsJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String preparationPlanJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String lastAgentResponse;

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

    public AgentSession() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Long getInterviewId() { return interviewId; }
    public void setInterviewId(Long interviewId) { this.interviewId = interviewId; }

    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }

    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }

    public String getSkillGapsJson() { return skillGapsJson; }
    public void setSkillGapsJson(String skillGapsJson) { this.skillGapsJson = skillGapsJson; }

    public String getCompletedTopicsJson() { return completedTopicsJson; }
    public void setCompletedTopicsJson(String completedTopicsJson) { this.completedTopicsJson = completedTopicsJson; }

    public String getPendingTopicsJson() { return pendingTopicsJson; }
    public void setPendingTopicsJson(String pendingTopicsJson) { this.pendingTopicsJson = pendingTopicsJson; }

    public String getPreparationPlanJson() { return preparationPlanJson; }
    public void setPreparationPlanJson(String preparationPlanJson) { this.preparationPlanJson = preparationPlanJson; }

    public String getLastAgentResponse() { return lastAgentResponse; }
    public void setLastAgentResponse(String lastAgentResponse) { this.lastAgentResponse = lastAgentResponse; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
