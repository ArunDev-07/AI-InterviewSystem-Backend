package com.example.AI_InterviewSystem.Repository;

import com.example.AI_InterviewSystem.Model.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepo extends JpaRepository<Users , Long> {
    Users findByUsername (String username) ;

}
