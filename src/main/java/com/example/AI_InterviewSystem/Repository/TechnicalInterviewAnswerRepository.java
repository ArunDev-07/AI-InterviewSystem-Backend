package com.example.AI_InterviewSystem.Repository;

import com.example.AI_InterviewSystem.Model.TechnicalInterviewAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TechnicalInterviewAnswerRepository extends JpaRepository<TechnicalInterviewAnswer, Long> {
    List<TechnicalInterviewAnswer> findByInterviewIdOrderByIdAsc(Long interviewId);
}