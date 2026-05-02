package com.example.AI_InterviewSystem.Dto;

import lombok.Data;

@Data
public class TechnicalInterviewAnswerRequest {
    private Long interviewId;
    private String question;
    private String answer;
    private String topic;
}