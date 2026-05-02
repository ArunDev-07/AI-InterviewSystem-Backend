package com.example.AI_InterviewSystem.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "technical_interview_answers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TechnicalInterviewAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long interviewId;

    private String username;

    @Column(columnDefinition = "TEXT")
    private String question;

    private String topic;

    @Column(columnDefinition = "LONGTEXT")
    private String answer;

    @Column(columnDefinition = "LONGTEXT")
    private String aiFeedback;

    @Column(columnDefinition = "LONGTEXT")
    private String optimalAnswer;

    private Integer score = 0;

    private LocalDateTime createdAt = LocalDateTime.now();
}