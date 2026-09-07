package com.example.AI_InterviewSystem.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AgentChatRequest {

    @NotBlank(message = "message is required")
    @Size(max = 8000, message = "message is too long")
    private String message;

    // Optional: continue an existing prep session.
    private Long sessionId;

    // Optional: which of the user's interviews to scope resume search to.
    // If omitted on a NEW session, AgentService defaults to the user's most
    // recent interview. Ignored if sessionId is provided (session already has one).
    private Long interviewId;

    @Size(max = 200)
    private String targetRole;

    @Size(max = 20000)
    private String jobDescription;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getInterviewId() { return interviewId; }
    public void setInterviewId(Long interviewId) { this.interviewId = interviewId; }

    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }

    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
}
