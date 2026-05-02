package com.example.AI_InterviewSystem.Dto;

import lombok.Data;

@Data
public class RoundSubmitRequest {

    private String question;
    private String answer;
    private String code;
    private String language;
}