package com.example.AI_InterviewSystem.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_rounds")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One interview has 4 rounds
    @ManyToOne
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @Enumerated(EnumType.STRING)
    private RoundType roundType;

    @Enumerated(EnumType.STRING)
    private RoundStatus status = RoundStatus.NOT_STARTED;

    private Integer score = 0;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "LONGTEXT")
    private String answer;

    @Column(columnDefinition = "LONGTEXT")
    private String code;

    private String language;

    @Column(columnDefinition = "LONGTEXT")
    private String aiFeedback;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;
}