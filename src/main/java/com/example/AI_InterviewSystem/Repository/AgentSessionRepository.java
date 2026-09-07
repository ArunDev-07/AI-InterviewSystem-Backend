package com.example.AI_InterviewSystem.Repository;

import com.example.AI_InterviewSystem.Model.AgentSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentSessionRepository extends JpaRepository<AgentSession, Long> {

    List<AgentSession> findByUsernameOrderByCreatedAtDesc(String username);

    // RBAC-safe ownership check before returning/updating a session.
    boolean existsByIdAndUsername(Long id, String username);
}
