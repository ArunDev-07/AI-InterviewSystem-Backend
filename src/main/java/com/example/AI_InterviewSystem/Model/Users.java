package com.example.AI_InterviewSystem.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    // ROLE as String (USER / ADMIN)
    @Column(nullable = false)
    private String role = "USER";

    @Column(unique = true)
    private String email;

    private String phone;

    private boolean isActive = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // 🔥 Automatically set when inserting
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // 🔥 Automatically update
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}