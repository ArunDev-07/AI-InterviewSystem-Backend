package com.example.AI_InterviewSystem.Controller;

import com.example.AI_InterviewSystem.Dto.AgentChatRequest;
import com.example.AI_InterviewSystem.Dto.AgentChatResponse;
import com.example.AI_InterviewSystem.Model.AgentSession;
import com.example.AI_InterviewSystem.Service.TechnicalInterviewService;
import com.example.AI_InterviewSystem.agent.AgentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Uses authentication.getName() to get the username, same as InterviewService's
 * existing methods (startInterview(String username), getMyInterviews(String username))
 * expect — matching your app's identity pattern rather than a custom UserPrincipal
 * cast. If your actual controllers for Aptitude/DSA/HR do it differently (e.g. via
 * a custom UserDetails implementation whose getUsername() you call explicitly),
 * swap the one line in currentUsername() to match exactly.
 *
 * No @PreAuthorize role restriction beyond "must be authenticated" — available to
 * any logged-in candidate, same as the interview rounds.
 */
@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;
    private final TechnicalInterviewService technicalInterviewService;

    public AgentController(AgentService agentService, TechnicalInterviewService technicalInterviewService) {
        this.agentService = agentService;
        this.technicalInterviewService = technicalInterviewService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AgentChatResponse> chat(
            @Valid @RequestBody AgentChatRequest request,
            Authentication authentication
    ) {
        String username = currentUsername(authentication);
        return ResponseEntity.ok(agentService.chat(username, request));
    }

    /**
     * Same as /chat, but accepts an optional resume file (PDF) attached to this
     * turn — the "attach resume" version of the chat input, like Claude's
     * message + file combo. If a file is attached:
     *   1. Extract its text via TechnicalInterviewService.extractResumeText()
     *      — the SAME extraction method the existing "start technical interview"
     *      upload flow already uses. No new PDF parsing logic.
     *   2. Ingest it via TechnicalInterviewService.ingestResumeForAgentSession()
     *      — which itself calls the SAME PdfEmbeddingService.ingestResume() the
     *      technical-interview flow uses, just without the extra "generate 5
     *      interview questions" LLM call that flow also does.
     *   3. Set the resulting interviewId on the AgentChatRequest before handing
     *      off to AgentService.chat() as normal — the agent will see the resume
     *      is already in Chroma and can call searchResume on it immediately in
     *      the same turn.
     *
     * If no file is attached, behaves exactly like /chat (interviewId stays
     * whatever the request/session already had — see AgentService.resolveSession
     * for how an existing session picks up a newly-attached resume mid-conversation).
     */
    @PostMapping(value = "/chat-with-resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> chatWithResume(
            @RequestParam("message") String message,
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestParam(value = "targetRole", required = false) String targetRole,
            @RequestParam(value = "jobDescription", required = false) String jobDescription,
            @RequestParam(value = "resume", required = false) MultipartFile resume,
            Authentication authentication
    ) {
        String username = currentUsername(authentication);

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }

        Long interviewId = null;
        if (resume != null && !resume.isEmpty()) {
            try {
                String resumeText = technicalInterviewService.extractResumeText(resume);
                interviewId = technicalInterviewService.ingestResumeForAgentSession(username, resumeText);
            } catch (Exception e) {
                // Fail loudly here rather than silently falling through to a
                // resumeless chat turn — the user explicitly attached a file
                // expecting it to be used, so a swallowed failure would be
                // confusing ("I attached it, why didn't it use it?").
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Could not process the attached resume: " + e.getMessage()));
            }
        }

        AgentChatRequest request = new AgentChatRequest();
        request.setMessage(message);
        request.setSessionId(sessionId);
        request.setInterviewId(interviewId);
        request.setTargetRole(targetRole);
        request.setJobDescription(jobDescription);

        return ResponseEntity.ok(agentService.chat(username, request));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<AgentSession>> listSessions(Authentication authentication) {
        String username = currentUsername(authentication);
        return ResponseEntity.ok(agentService.listSessions(username));
    }

    private String currentUsername(Authentication authentication) {
        return authentication.getName();
    }
}