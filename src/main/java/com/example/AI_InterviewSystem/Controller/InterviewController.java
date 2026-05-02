package com.example.AI_InterviewSystem.Controller;

import com.example.AI_InterviewSystem.Dto.RoundSubmitRequest;
import com.example.AI_InterviewSystem.Model.Interview;
import com.example.AI_InterviewSystem.Model.InterviewRound;
import com.example.AI_InterviewSystem.Service.InterviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
@CrossOrigin(origins = "http://localhost:5173")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    // USER starts interview
    @PostMapping("/start")
    public ResponseEntity<Interview> startInterview(Authentication authentication) {

        String username = authentication.getName();

        Interview interview = interviewService.startInterview(username);

        return ResponseEntity.ok(interview);
    }

    // USER can see own interviews
    @GetMapping("/my")
    public ResponseEntity<List<Interview>> getMyInterviews(Authentication authentication) {

        String username = authentication.getName();

        return ResponseEntity.ok(interviewService.getMyInterviews(username));
    }

    // ADMIN can see all interviews
    @GetMapping("/admin/all")
    public ResponseEntity<List<Interview>> getAllInterviews() {

        return ResponseEntity.ok(interviewService.getAllInterviews());
    }

    // Get 4 rounds of one interview
    @GetMapping("/{interviewId}/rounds")
    public ResponseEntity<List<InterviewRound>> getRounds(@PathVariable Long interviewId) {

        return ResponseEntity.ok(interviewService.getRounds(interviewId));
    }

    // Start one round
    @PostMapping("/rounds/{roundId}/start")
    public ResponseEntity<InterviewRound> startRound(@PathVariable Long roundId) {

        return ResponseEntity.ok(interviewService.startRound(roundId));
    }

    // Submit one round
    @PostMapping("/rounds/{roundId}/submit")
    public ResponseEntity<InterviewRound> submitRound(
            @PathVariable Long roundId,
            @RequestBody RoundSubmitRequest request
    ) {

        return ResponseEntity.ok(interviewService.submitRound(roundId, request));
    }

    // Final result
    @GetMapping("/{interviewId}/final-result")
    public ResponseEntity<Interview> getFinalResult(@PathVariable Long interviewId) {

        return ResponseEntity.ok(interviewService.getFinalResult(interviewId));
    }
}