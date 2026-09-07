package com.example.AI_InterviewSystem.agent;

/**
 * Carries the authenticated user's identity for the duration of one agent
 * invocation, so tools can scope lookups correctly WITHOUT the LLM ever
 * supplying or being able to tamper with these values.
 *
 * Matches your actual identity model: your app resolves the current user by
 * USERNAME (UserRepo#findByUsername), and your resume RAG
 * (PdfEmbeddingService#retrieveRelevantChunks) is scoped by BOTH username AND
 * interviewId — resumes are ingested per interview attempt, not once per user.
 * So both values travel together here, not just a userId.
 *
 * interviewId MAY LEGITIMATELY BE NULL. AgentService no longer auto-defaults a
 * new session to the user's most recently started technical interview — it only
 * binds an interviewId when the request explicitly supplies one. A null value
 * here means "no resume is bound to this session yet," which resume-dependent
 * tools (e.g. ResumeRagTool) must check for and handle explicitly rather than
 * querying with a null/stale id.
 *
 * AgentService sets this immediately after resolving the session, before
 * calling the agent, and clears it in a finally block.
 *
 * FIX (2026-08-31) — lastVerifiedGithubUsername:
 * MessageWindowChatMemory.withMaxMessages(N) counts every message generated
 * during tool-calling, including intermediate assistant/tool-result pairs
 * WITHIN a single turn. On long tool chains (getCandidateProfile ->
 * searchResume -> analyzeGithub -> analyzeSkillGap -> saveCandidateProfile)
 * the window can evict the original successful analyzeGithub(username) call
 * and its result before the model needs it again later in the SAME turn —
 * observed in production logs as the model re-calling analyzeGithub("") a
 * few tool calls later and never recovering.
 *
 * This is a request-scoped (NOT session/DB-persisted) cache: it only survives
 * for the lifetime of one AgentService.chat() call, same lifecycle as the rest
 * of this class, and is cleared in the same finally block. It does not bypass
 * any security boundary — it only ever stores a username that GithubTool
 * itself already successfully looked up earlier in this exact request, for
 * this exact user's request. GithubTool uses it strictly as a fallback when
 * the model supplies a blank/placeholder value, not as a way to skip
 * validation for a new value.
 */
public final class AgentRequestContext {

    public record Context(String username, Long interviewId) {}

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> LAST_VERIFIED_GITHUB_USERNAME = new ThreadLocal<>();

    private AgentRequestContext() {}

    public static void set(String username, Long interviewId) {
        CURRENT.set(new Context(username, interviewId));
        LAST_VERIFIED_GITHUB_USERNAME.remove();
    }

    public static String username() {
        return require().username();
    }

    /**
     * May legitimately return null — callers (tools) must check for this rather
     * than assuming a resume is always available. Null means no technical
     * interview / ingested resume is bound to the current session.
     */
    public static Long interviewId() {
        return require().interviewId();
    }

    /**
     * Called by GithubTool immediately after a real, successful GitHub lookup —
     * never with an unverified/placeholder value.
     */
    public static void rememberGithubUsername(String verifiedUsername) {
        if (verifiedUsername != null && !verifiedUsername.isBlank()) {
            LAST_VERIFIED_GITHUB_USERNAME.set(verifiedUsername);
        }
    }

    /** May return null if no successful GitHub lookup has happened yet this request. */
    public static String lastVerifiedGithubUsername() {
        return LAST_VERIFIED_GITHUB_USERNAME.get();
    }

    private static Context require() {
        Context ctx = CURRENT.get();
        if (ctx == null) {
            throw new IllegalStateException(
                    "No agent request context bound. AgentService must call " +
                            "AgentRequestContext.set(username, interviewId) before invoking the agent."
            );
        }
        return ctx;
    }

    public static void clear() {
        CURRENT.remove();
        LAST_VERIFIED_GITHUB_USERNAME.remove();
    }
}