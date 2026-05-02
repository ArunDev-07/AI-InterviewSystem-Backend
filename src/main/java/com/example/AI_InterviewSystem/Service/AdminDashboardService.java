package com.example.AI_InterviewSystem.Service;

import com.example.AI_InterviewSystem.Model.CodeAnalysisResult;
import com.example.AI_InterviewSystem.Model.Users;
import com.example.AI_InterviewSystem.Repository.CodeAnalysisResultRepo;
import com.example.AI_InterviewSystem.Repository.UserRepo;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AdminDashboardService {

    private final UserRepo userRepo;
    private final CodeAnalysisResultRepo resultRepo;

    public AdminDashboardService(UserRepo userRepo, CodeAnalysisResultRepo resultRepo) {
        this.userRepo = userRepo;
        this.resultRepo = resultRepo;
    }

    public Map<String, Object> getDashboardStats() {
        long totalUsers = userRepo.count();
        long totalSubmissions = resultRepo.count();

        List<CodeAnalysisResult> results = resultRepo.findAll();

        double averageScore = results.stream()
                .filter(r -> r.getScore() != null)
                .mapToInt(CodeAnalysisResult::getScore)
                .average()
                .orElse(0.0);

        return Map.of(
                "totalUsers", totalUsers,
                "totalSubmissions", totalSubmissions,
                "averageScore", averageScore
        );
    }

    public List<Users> getAllUsers() {
        List<Users> users = userRepo.findAll();
        users.forEach(user -> user.setPassword(null));
        return users;
    }

    public Page<CodeAnalysisResult> getAllResultsPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("submittedAt").descending());
        return resultRepo.findAll(pageable);
    }

    public List<CodeAnalysisResult> getUserResults(String username) {
        return resultRepo.findByUsername(username);
    }

    public void deleteResult(Long id) {
        if (!resultRepo.existsById(id)) {
            throw new RuntimeException("Result not found");
        }
        resultRepo.deleteById(id);
    }

    public List<Map<String, Object>> getCandidateRankings() {
        List<Users> users = userRepo.findAll();
        List<Map<String, Object>> rankings = new ArrayList<>();

        for (Users user : users) {
            if ("ADMIN".equalsIgnoreCase(user.getRole())) continue;

            List<CodeAnalysisResult> results = resultRepo.findByUsername(user.getUsername());

            int totalScore = results.stream()
                    .filter(r -> r.getScore() != null)
                    .mapToInt(CodeAnalysisResult::getScore)
                    .sum();

            double averageScore = results.stream()
                    .filter(r -> r.getScore() != null)
                    .mapToInt(CodeAnalysisResult::getScore)
                    .average()
                    .orElse(0.0);

            String status;

            if (averageScore >= 8) {
                status = "SELECTED";
            } else if (averageScore >= 6) {
                status = "HOLD";
            } else {
                status = "REJECTED";
            }

            Map<String, Object> candidate = new HashMap<>();
            candidate.put("username", user.getUsername());
            candidate.put("email", user.getEmail());
            candidate.put("phone", user.getPhone());
            candidate.put("submissions", results.size());
            candidate.put("totalScore", totalScore);
            candidate.put("averageScore", averageScore);
            candidate.put("status", status);

            rankings.add(candidate);
        }

        rankings.sort((a, b) ->
                Double.compare(
                        (double) b.get("averageScore"),
                        (double) a.get("averageScore")
                )
        );

        return rankings;
    }

    public List<Map<String, Object>> getTop5Candidates() {
        return getCandidateRankings()
                .stream()
                .limit(5)
                .toList();
    }
}