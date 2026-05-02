package com.example.AI_InterviewSystem.Controller;

import com.example.AI_InterviewSystem.Dto.TechnicalInterviewAnswerRequest;
import com.example.AI_InterviewSystem.Dto.TechnicalInterviewNextQuestionRequest;
import com.example.AI_InterviewSystem.Dto.TechnicalInterviewStartRequest;
import com.example.AI_InterviewSystem.Service.TechnicalInterviewService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/technical-interview")
@CrossOrigin(origins = "http://localhost:5173")
public class TechnicalInterviewController {

    private final TechnicalInterviewService technicalInterviewService;

    public TechnicalInterviewController(TechnicalInterviewService technicalInterviewService) {
        this.technicalInterviewService = technicalInterviewService;
    }

    // Upload resume PDF and extract text
    @PostMapping(
            value = "/upload-resume",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> uploadResume(@RequestParam("file") MultipartFile file) {
        try {
            String resumeText = technicalInterviewService.extractResumeText(file);

            return ResponseEntity.ok(Map.of(
                    "message", "Resume uploaded successfully",
                    "resumeText", resumeText
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // Start resume-based technical interview
    @PostMapping("/start")
    public ResponseEntity<?> startInterview(
            @RequestBody TechnicalInterviewStartRequest request,
            Authentication authentication
    ) {
        try {
            String username = authentication.getName();

            return ResponseEntity.ok(
                    technicalInterviewService.startInterview(
                            username,
                            request.getResumeText()
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // Submit user answer and get AI evaluation
    @PostMapping("/answer")
    public ResponseEntity<?> submitAnswer(
            @RequestBody TechnicalInterviewAnswerRequest request,
            Authentication authentication
    ) {
        try {
            String username = authentication.getName();

            return ResponseEntity.ok(
                    technicalInterviewService.submitAnswer(username, request)
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // Real interview flow: generate next question based on previous answer
    @PostMapping("/next-question")
    public ResponseEntity<?> generateNextQuestion(
            @RequestBody TechnicalInterviewNextQuestionRequest request,
            Authentication authentication
    ) {
        try {
            String username = authentication.getName();

            return ResponseEntity.ok(
                    technicalInterviewService.generateNextQuestion(
                            username,
                            request.getInterviewId(),
                            request.getPreviousQuestion(),
                            request.getPreviousAnswer(),
                            request.getPreviousFeedback()
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // Complete interview and generate final report
    @PostMapping("/{interviewId}/complete")
    public ResponseEntity<?> completeInterview(
            @PathVariable Long interviewId,
            Authentication authentication
    ) {
        try {
            String username = authentication.getName();

            return ResponseEntity.ok(
                    technicalInterviewService.completeInterview(username, interviewId)
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // Get final interview result
    @GetMapping("/{interviewId}/result")
    public ResponseEntity<?> getResult(
            @PathVariable Long interviewId,
            Authentication authentication
    ) {
        try {
            String username = authentication.getName();

            return ResponseEntity.ok(
                    technicalInterviewService.getResult(username, interviewId)
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // Get logged-in user's technical interview history
    @GetMapping("/my")
    public ResponseEntity<?> getMyInterviews(Authentication authentication) {
        try {
            String username = authentication.getName();

            return ResponseEntity.ok(
                    technicalInterviewService.getMyInterviews(username)
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}