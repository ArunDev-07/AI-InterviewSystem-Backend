package com.example.AI_InterviewSystem.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "interviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many interviews belong to one user
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    private InterviewStatus status = InterviewStatus.STARTED;

    private Integer totalScore = 0;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InterviewRound> rounds = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.startedAt = LocalDateTime.now();
    }
}