package com.example.AI_InterviewSystem.Dto;

import lombok.Data;

@Data
public class TechnicalInterviewNextQuestionRequest {
    private Long interviewId;
    private String previousQuestion;
    private String previousAnswer;
    private String previousFeedback;
}