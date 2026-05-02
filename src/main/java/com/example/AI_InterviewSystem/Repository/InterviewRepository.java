package com.example.AI_InterviewSystem.Repository;

import com.example.AI_InterviewSystem.Model.Interview;
import com.example.AI_InterviewSystem.Model.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findByUser(Users user);
    List<Interview> findByUserUsernameOrderByStartedAtDesc(String username);

    List<Interview> findAllByOrderByStartedAtDesc();
}