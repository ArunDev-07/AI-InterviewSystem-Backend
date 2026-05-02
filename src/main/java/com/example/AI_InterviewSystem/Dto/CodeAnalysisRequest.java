package com.example.AI_InterviewSystem.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CodeAnalysisRequest {
    private String problem;
    private String language;
    private String code;
}