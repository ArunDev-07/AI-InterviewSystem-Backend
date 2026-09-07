package com.example.AI_InterviewSystem.Dto;

public class ToolExecutionEvent {

    private String toolName;
    private String status; // SUCCESS | FAILED | SKIPPED_LIMIT
    private long durationMs;
    private String errorMessage; // null on success
    // Truncated preview of the ACTUAL arguments the model passed into this tool call.
    // This is the concrete evidence that tool N really used tool (N-1)'s real output,
    // not just that both tools happened to run — visible in the API response itself,
    // not just server logs.
    private String inputPreview;

    public ToolExecutionEvent() {}

    public ToolExecutionEvent(String toolName, String status, long durationMs, String errorMessage, String inputPreview) {
        this.toolName = toolName;
        this.status = status;
        this.durationMs = durationMs;
        this.errorMessage = errorMessage;
        this.inputPreview = inputPreview;
    }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getInputPreview() { return inputPreview; }
    public void setInputPreview(String inputPreview) { this.inputPreview = inputPreview; }
}