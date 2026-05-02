package com.example.AI_InterviewSystem.Repository;

import com.example.AI_InterviewSystem.Model.TechnicalInterview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TechnicalInterviewRepository extends JpaRepository<TechnicalInterview, Long> {
    List<TechnicalInterview> findByUsernameOrderByStartedAtDesc(String username);
}