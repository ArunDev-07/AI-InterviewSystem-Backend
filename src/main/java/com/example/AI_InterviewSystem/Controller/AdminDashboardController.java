package com.example.AI_InterviewSystem.Controller;

import com.example.AI_InterviewSystem.Model.CodeAnalysisResult;
import com.example.AI_InterviewSystem.Model.Users;
import com.example.AI_InterviewSystem.Service.AdminDashboardService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:5173")
public class AdminDashboardController {

    private final AdminDashboardService adminService;

    public AdminDashboardController(AdminDashboardService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @GetMapping("/users")
    public ResponseEntity<List<Users>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @GetMapping("/results")
    public ResponseEntity<Page<CodeAnalysisResult>> getAllResults(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(adminService.getAllResultsPaginated(page, size));
    }

    @GetMapping("/users/{username}/results")
    public ResponseEntity<List<CodeAnalysisResult>> getUserResults(@PathVariable String username) {
        return ResponseEntity.ok(adminService.getUserResults(username));
    }

    @GetMapping("/candidates/rankings")
    public ResponseEntity<List<Map<String, Object>>> getCandidateRankings() {
        return ResponseEntity.ok(adminService.getCandidateRankings());
    }

    @GetMapping("/candidates/top5")
    public ResponseEntity<List<Map<String, Object>>> getTop5Candidates() {
        return ResponseEntity.ok(adminService.getTop5Candidates());
    }

    @DeleteMapping("/results/{id}")
    public ResponseEntity<?> deleteResult(@PathVariable Long id) {
        adminService.deleteResult(id);
        return ResponseEntity.ok(Map.of("message", "Result deleted successfully"));
    }
}