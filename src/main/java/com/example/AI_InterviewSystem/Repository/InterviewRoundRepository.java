package com.example.AI_InterviewSystem.Repository;

import com.example.AI_InterviewSystem.Model.Interview;
import com.example.AI_InterviewSystem.Model.InterviewRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewRoundRepository extends JpaRepository<InterviewRound, Long> {

    List<InterviewRound> findByInterview(Interview interview);
    List<InterviewRound> findByInterviewOrderByIdAsc(Interview interview);
}