package com.example.AI_InterviewSystem.agent;

import com.example.AI_InterviewSystem.Dto.AgentChatRequest;
import com.example.AI_InterviewSystem.Dto.AgentChatResponse;
import com.example.AI_InterviewSystem.Dto.ToolExecutionEvent;
import com.example.AI_InterviewSystem.Model.AgentSession;
import com.example.AI_InterviewSystem.Repository.AgentSessionRepository;
import com.example.AI_InterviewSystem.Service.TechnicalInterviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final InterviewPreparationAgent agent;
    private final AgentSessionRepository sessionRepository;
    private final TechnicalInterviewService technicalInterviewService;

    public AgentService(
            InterviewPreparationAgent agent,
            AgentSessionRepository sessionRepository,
            TechnicalInterviewService technicalInterviewService
    ) {
        this.agent = agent;
        this.sessionRepository = sessionRepository;
        this.technicalInterviewService = technicalInterviewService;
    }

    /**
     * Runs one turn of the agent for an authenticated user (identified by username,
     * matching the rest of your app). Handles:
     * - session ownership/creation (RBAC-safe: never touches another user's session)
     * - resolving which technical interview's ingested resume (if any) to scope this
     *   session to — NOTE: no longer defaults to "most recent interview." See
     *   resolveInterviewId() below.
     * - binding AgentRequestContext so tools can't be pointed at another user's/
     *   interview's data
     * - draining tool activity for the UI checklist
     * - persisting session state
     * - top-level failure isolation (Ollama down, etc.) without leaking internals
     */
    public AgentChatResponse chat(String username, AgentChatRequest request) {
        long start = System.currentTimeMillis();

        AgentSession session = resolveSession(username, request);

        AgentRequestContext.set(username, session.getInterviewId());
        try {
            String enrichedMessage = enrichWithSessionContext(request.getMessage(), session);

            log.info("Agent request start — username={}, sessionId={}, interviewId={}",
                    username, session.getId(), session.getInterviewId());

            String reply;
            try {
                reply = agent.chat(session.getId(), enrichedMessage);
                if (reply == null || reply.isBlank()) {
                    // No exception was thrown, but the model still produced nothing usable —
                    // seen in testing with small local models after a tool call completes.
                    // Treat this the same as a failure rather than leaking null to the API
                    // response, which would break a frontend expecting a string.
                    log.warn("Agent returned null/blank reply with no exception — username={}, sessionId={}",
                            username, session.getId());
                    reply = "The reasoning model didn't return a usable answer for this request. " +
                            "This can happen with smaller local models after a tool call — try " +
                            "rephrasing your request, or try again.";
                }
            } catch (Exception e) {
                // Covers Ollama unavailable / model error / timeout at the top level.
                log.error("Agent invocation failed — username={}, sessionId={}: {}",
                        username, session.getId(), e.getMessage());
                reply = "I couldn't reach the reasoning model just now, so I can't process this " +
                        "request. Please try again in a moment. (If this keeps happening, the " +
                        "Ollama service may be down.)";
            }

            session.setLastAgentResponse(reply);
            sessionRepository.save(session);

            List<ToolExecutionEvent> toolActivity = ToolActivityRecorder.drain();
            long duration = System.currentTimeMillis() - start;

            log.info("Agent request complete — username={}, sessionId={}, tools={}, durationMs={}",
                    username, session.getId(), toolActivity.size(), duration);

            return new AgentChatResponse(session.getId(), reply, toolActivity, duration);

        } finally {
            AgentRequestContext.clear();
            ToolActivityRecorder.clear();
        }
    }

    public List<AgentSession> listSessions(String username) {
        return sessionRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    private AgentSession resolveSession(String username, AgentChatRequest request) {
        if (request.getSessionId() != null) {
            boolean owned = sessionRepository.existsByIdAndUsername(request.getSessionId(), username);
            if (!owned) {
                // Do not leak whether the session exists for another user — treat as new.
                log.warn("Rejected cross-user session access attempt — username={}, requestedSessionId={}",
                        username, request.getSessionId());
            } else {
                AgentSession existing = sessionRepository.findById(request.getSessionId())
                        .orElseGet(() -> createSession(username, request));

                // If the user just attached a resume mid-conversation (e.g. via
                // /chat-with-resume) and this session doesn't have one bound yet,
                // bind it now. Without this, an existing session's interviewId
                // would stay null forever even after a resume is uploaded into
                // it, since a brand-new session is only created once per
                // conversation — subsequent turns always hit this branch instead.
                if (existing.getInterviewId() == null && request.getInterviewId() != null) {
                    existing.setInterviewId(request.getInterviewId());
                    existing = sessionRepository.save(existing);
                    log.info("Bound newly-attached resume to existing session — sessionId={}, interviewId={}",
                            existing.getId(), existing.getInterviewId());
                }

                return existing;
            }
        }
        return createSession(username, request);
    }

    private AgentSession createSession(String username, AgentChatRequest request) {
        Long interviewId = resolveInterviewId(username, request);

        AgentSession session = new AgentSession();
        session.setUsername(username);
        session.setInterviewId(interviewId);
        session.setTargetRole(request.getTargetRole());
        session.setJobDescription(request.getJobDescription());
        return sessionRepository.save(session);
    }

    /**
     * Only trust an EXPLICIT interviewId from the request (e.g. the frontend passed
     * one because the user clicked "prep for this interview" from a specific
     * technical-interview screen).
     *
     * Deliberately does NOT default to "the user's most recent technical interview"
     * anymore. That used to mean every new session — regardless of what the user
     * actually asked — was silently pre-bound to whichever resume happened to be
     * ingested most recently, even for requests that had nothing to do with a
     * resume, or where the user meant a different attempt. Leaving this null when
     * unspecified means:
     *   - AgentRequestContext carries interviewId = null for this session
     *   - ResumeRagTool.searchResume() will see that null and tell the agent there's
     *     no resume bound to this session, instead of silently querying stale data
     *   - the agent (per its system prompt) will then naturally ask the user to
     *     upload a resume or specify which interview to use — the same "ask for
     *     what's missing" behavior you'd get from Claude, rather than guessing.
     *
     * technicalInterviewService is still injected/available here if you later want
     * to offer the user a pick-list of their past interviews instead of forcing a
     * blind auto-select.
     */
    private Long resolveInterviewId(String username, AgentChatRequest request) {
        if (request.getInterviewId() != null) {
            return request.getInterviewId();
        }
        return null;
    }

    private String enrichWithSessionContext(String userMessage, AgentSession session) {
        StringBuilder sb = new StringBuilder();
        if (session.getTargetRole() != null && !session.getTargetRole().isBlank()) {
            sb.append("[Target role: ").append(session.getTargetRole()).append("]\n");
        }
        if (session.getJobDescription() != null && !session.getJobDescription().isBlank()) {
            sb.append("[Job description provided for this session — use analyzeJobDescription on it if relevant:\n")
                    .append(session.getJobDescription()).append("\n]\n");
        }
        sb.append(userMessage);
        return sb.toString();
    }
}