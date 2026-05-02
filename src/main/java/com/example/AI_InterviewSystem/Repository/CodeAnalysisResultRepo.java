package com.example.AI_InterviewSystem.Repository;

import com.example.AI_InterviewSystem.Model.CodeAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeAnalysisResultRepo extends JpaRepository<CodeAnalysisResult, Long> {

    List<CodeAnalysisResult> findByUsername(String username);
}