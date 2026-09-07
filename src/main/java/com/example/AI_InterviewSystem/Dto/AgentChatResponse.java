package com.example.AI_InterviewSystem.Dto;

import java.util.List;

public class AgentChatResponse {

    private Long sessionId;
    private String reply;
    private List<ToolExecutionEvent> toolActivity;
    private long totalDurationMs;

    public AgentChatResponse() {}

    public AgentChatResponse(Long sessionId, String reply, List<ToolExecutionEvent> toolActivity, long totalDurationMs) {
        this.sessionId = sessionId;
        this.reply = reply;
        this.toolActivity = toolActivity;
        this.totalDurationMs = totalDurationMs;
    }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public List<ToolExecutionEvent> getToolActivity() { return toolActivity; }
    public void setToolActivity(List<ToolExecutionEvent> toolActivity) { this.toolActivity = toolActivity; }

    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
}
