package com.example.AI_InterviewSystem.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TechnicalInterviewQuestionDto {
    private Integer id;
    private String question;
    private String topic;
    private String difficulty;
}