package com.example.AI_InterviewSystem.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "code_analysis_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @Column(columnDefinition = "TEXT")
    private String problem;

    private String language;

    @Column(columnDefinition = "LONGTEXT")
    private String code;

    @Column(columnDefinition = "LONGTEXT")
    private String aiResponse;

    private Integer score;

    private String timeComplexity;

    private String spaceComplexity;

    private LocalDateTime submittedAt;

    @PrePersist
    public void prePersist() {
        this.submittedAt = LocalDateTime.now();
    }
}