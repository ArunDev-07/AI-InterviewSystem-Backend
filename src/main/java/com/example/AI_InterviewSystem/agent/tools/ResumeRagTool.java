package com.example.AI_InterviewSystem.agent.tools;

import com.example.AI_InterviewSystem.Service.PdfEmbeddingService;
import com.example.AI_InterviewSystem.agent.AgentRequestContext;
import com.example.AI_InterviewSystem.agent.ToolActivityRecorder;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Wraps your EXISTING resume RAG exactly as-is — calls straight into
 * PdfEmbeddingService.retrieveRelevantChunks(username, interviewId, query, topN),
 * which is already scoped by both username and interviewId via ChromaService's
 * $and where-filter. No new retrieval logic, no new ChromaDB calls added here.
 *
 * username + interviewId are deliberately NOT tool parameters the LLM fills in —
 * they're read from AgentRequestContext, so the model can never be tricked into
 * querying another user's (or another interview's) resume chunks.
 *
 * interviewId can legitimately be null now (AgentService no longer auto-binds a
 * session to the user's "most recent" interview). When it's null, there is no
 * resume associated with this session — this tool must say so explicitly rather
 * than calling retrieveRelevantChunks with a null id (which would either throw
 * or silently return nothing meaningful, masking the real cause).
 *
 * NOTE: retrieveRelevantChunks() already fails soft (catches embedding/Chroma
 * errors internally and returns List.of()) per your existing implementation, so
 * this tool will never throw for "Chroma unreachable" — it'll just come back
 * with "no relevant resume content found," which is the correct agent-facing
 * behavior (let the LLM say "I couldn't verify that from your resume" instead
 * of crashing the whole multi-step run).
 */
@Component
public class ResumeRagTool {

    private static final int TOP_N = 5;

    private final PdfEmbeddingService pdfEmbeddingService;

    public ResumeRagTool(PdfEmbeddingService pdfEmbeddingService) {
        this.pdfEmbeddingService = pdfEmbeddingService;
    }

    @Tool("""
        Search the candidate's resume for specific information. Use this whenever you need
        facts about the candidate: skills, technologies used, projects, work experience,
        education, or certifications. Pass a focused query, e.g. 'Spring Boot experience'
        or 'database technologies used in projects'. Do not call this for information that
        is not about the candidate's own resume.
        """)
    public String searchResume(String query) {
        // Guard against the model calling this with no real query (seen in testing —
        // it sometimes invokes the tool without supplying an argument). Falling
        // through to PdfEmbeddingService with a null/blank query makes it silently
        // return nothing, so this whole tool call becomes a no-op that LOOKS like
        // it ran but retrieved zero real resume content. Default to a broad query
        // instead, so the tool always attempts a real search.
        String effectiveQuery = (query == null || query.isBlank())
                ? "skills, experience, projects, technologies"
                : query;

        return ToolActivityRecorder.track("searchResume", "query=" + effectiveQuery, () -> {
            String username = AgentRequestContext.username();
            Long interviewId = AgentRequestContext.interviewId();

            if (interviewId == null) {
                // No technical interview (and therefore no ingested resume) is bound to
                // this session. Do NOT fall back to guessing/using any other interview's
                // resume — tell the agent plainly so it can ask the user, the same way
                // it would ask for any other missing required input.
                return "No resume is associated with this session yet. Ask the user to " +
                        "either upload their resume (by starting a technical interview) or " +
                        "specify which of their existing interview attempts' resume to use. " +
                        "Do not fabricate resume content or assume a previously used resume.";
            }

            List<String> chunks = pdfEmbeddingService.retrieveRelevantChunks(username, interviewId, effectiveQuery, TOP_N);

            if (chunks == null || chunks.isEmpty()) {
                return "No relevant resume content found for query: " + effectiveQuery;
            }
            return String.join("\n---\n", chunks);
        });
    }
}