package com.example.AI_InterviewSystem.Repository;

import com.example.AI_InterviewSystem.Model.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Same pattern as AgentSessionRepository — plain JpaRepository + explicit,
 * narrow finder methods. Deliberately does NOT expose a findAll()-style method
 * to callers outside this package's Service layer; CandidateProfileService is
 * the only intended caller, and it only ever looks up by the authenticated
 * username (see AgentRequestContext).
 */
public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {

    Optional<CandidateProfile> findByUsername(String username);

    boolean existsByUsername(String username);
}