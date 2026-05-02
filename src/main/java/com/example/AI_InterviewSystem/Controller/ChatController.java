package com.example.AI_InterviewSystem.Controller;

import com.example.AI_InterviewSystem.Model.CodeAnalysisResult;
import com.example.AI_InterviewSystem.Repository.CodeAnalysisResultRepo;
import com.example.AI_InterviewSystem.Service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;
    private final CodeAnalysisResultRepo resultRepo;

    public ChatController(ChatService chatService,
                          CodeAnalysisResultRepo resultRepo) {
        this.chatService = chatService;
        this.resultRepo = resultRepo;
    }

    // =========================
    // ✅ CHAT API
    // =========================

    @GetMapping("/chat")
    public ResponseEntity<Map<String, String>> chatGet(@RequestParam String message) {
        try {
            String reply = chatService.ask(message);
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chatPost(@RequestBody Map<String, String> payload) {
        try {
            String reply = chatService.ask(payload.get("message"));
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // =========================
    // ❌ OLD ANALYZE (NO SAVE)
    // =========================

    @PostMapping("/analyze-code")
    public ResponseEntity<Map<String, String>> analyzeCode(@RequestBody Map<String, String> payload) {
        try {
            String code = payload.get("code");
            String language = payload.getOrDefault("language", "Java");
            String problem = payload.getOrDefault("problem", "No problem statement provided");

            if (code == null || code.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Code cannot be empty"));
            }

            String reply = chatService.analyzeCode(code, language, problem);
            return ResponseEntity.ok(Map.of("reply", reply));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // =========================
    // 🔥 MAIN API (SAVE + RETURN)
    // =========================

    @PostMapping("/analyze-code/submit")
    public ResponseEntity<?> submitAndSave(
            @RequestBody Map<String, String> payload,
            Authentication authentication
    ) {
        try {
            String code = payload.get("code");
            String language = payload.getOrDefault("language", "Java");
            String problem = payload.getOrDefault("problem", "No problem statement provided");

            if (code == null || code.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Code cannot be empty"));
            }

            String username = authentication.getName();

            // 🔥 AI CALL
            String aiReply = chatService.analyzeCode(code, language, problem);

            // 🔥 EXTRACT DATA
            Integer score = extractScore(aiReply);
            String timeComplexity = extractTimeComplexity(aiReply);
            String spaceComplexity = extractSpaceComplexity(aiReply);

            // 🔥 SAVE
            CodeAnalysisResult result = new CodeAnalysisResult();
            result.setUsername(username);
            result.setProblem(problem);
            result.setLanguage(language);
            result.setCode(code);
            result.setAiResponse(aiReply);
            result.setScore(score);
            result.setTimeComplexity(timeComplexity);
            result.setSpaceComplexity(spaceComplexity);

            CodeAnalysisResult saved = resultRepo.save(result);

            // 🔥 RETURN RESPONSE
            return ResponseEntity.ok(Map.of(
                    "aiResponse", aiReply,
                    "score", score,
                    "timeComplexity", timeComplexity,
                    "spaceComplexity", spaceComplexity,
                    "data", saved
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // =========================
    // 🔥 USER RESULTS
    // =========================

    @GetMapping("/analyze-code/my")
    public ResponseEntity<List<CodeAnalysisResult>> getMyResults(Authentication authentication) {
        return ResponseEntity.ok(resultRepo.findByUsername(authentication.getName()));
    }

    // =========================
    // 🔥 ADMIN RESULTS
    // =========================

    @GetMapping("/analyze-code/admin/all")
    public ResponseEntity<List<CodeAnalysisResult>> getAllResults() {
        return ResponseEntity.ok(resultRepo.findAll());
    }

    // =========================
    // 🔥 EXTRACT SCORE
    // =========================

    private Integer extractScore(String aiResponse) {
        try {
            Pattern pattern = Pattern.compile("(\\d+)\\s*/\\s*10", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(aiResponse);

            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            return 0;
        }
        return 0;
    }

    // =========================
    // 🔥 EXTRACT TIME COMPLEXITY
    // =========================

    private String extractTimeComplexity(String aiResponse) {
        try {
            Pattern pattern = Pattern.compile("O\\([^\\n\\r]+?\\)");
            Matcher matcher = pattern.matcher(aiResponse);

            if (matcher.find()) {
                return matcher.group();
            }
        } catch (Exception e) {
            return "Not found";
        }
        return "Not found";
    }

    // =========================
    // 🔥 EXTRACT SPACE COMPLEXITY
    // =========================

    private String extractSpaceComplexity(String aiResponse) {
        try {
            String lower = aiResponse.toLowerCase();
            int index = lower.indexOf("space");

            if (index == -1) return "Not found";

            String part = aiResponse.substring(index);

            Pattern pattern = Pattern.compile("O\\([^\\n\\r]+?\\)");
            Matcher matcher = pattern.matcher(part);

            if (matcher.find()) {
                return matcher.group();
            }
        } catch (Exception e) {
            return "Not found";
        }
        return "Not found";
    }

    @GetMapping("/aptitude-questions")
    public ResponseEntity<?> getAptitudeQuestions() {
        try {
            String response = chatService.generateAptitudeQuestions();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error generating questions");
        }
    }
}
