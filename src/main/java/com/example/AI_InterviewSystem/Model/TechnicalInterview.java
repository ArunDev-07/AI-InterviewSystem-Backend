package com.example.AI_InterviewSystem.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "technical_interviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TechnicalInterview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @Column(columnDefinition = "LONGTEXT")
    private String resumeText;

    @Column(columnDefinition = "LONGTEXT")
    private String questionsJson;

    private Integer totalScore = 0;

    private String status = "STARTED";

    private LocalDateTime startedAt = LocalDateTime.now();

    private LocalDateTime completedAt;
}