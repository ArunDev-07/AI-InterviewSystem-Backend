package com.example.AI_InterviewSystem.agent.tools;

import com.example.AI_InterviewSystem.Model.Interview;
import com.example.AI_InterviewSystem.Model.InterviewRound;
import com.example.AI_InterviewSystem.Service.InterviewService;
import com.example.AI_InterviewSystem.agent.AgentRequestContext;
import com.example.AI_InterviewSystem.agent.ToolActivityRecorder;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reuses your EXISTING interview evaluation data — no new persistence added.
 * Wraps InterviewService.getMyInterviews() / getRounds(), same as your other
 * controllers already call.
 *
 * IMPORTANT — reflects a real characteristic of your current scoring:
 * InterviewService.calculateScore() currently returns a FIXED value per round
 * type (DSA=30, Aptitude=25, Communication=25, HR=20) regardless of actual
 * performance — it's a documented placeholder in your own code ("Simple
 * temporary score logic... Later you can extract score from AI response").
 * That means the numeric score can't tell the agent anything about strengths
 * or weaknesses yet. This tool surfaces the score for context but leans on the
 * aiFeedback TEXT as the actual signal, and says so explicitly in its output,
 * so the agent's system prompt (which says "do not fabricate... history you
 * were not given by a tool") doesn't accidentally treat a placeholder number
 * as real performance data.
 *
 * Once you replace calculateScore() with real extraction from the AI feedback,
 * this tool needs no changes — it'll automatically start reflecting genuine
 * variation, since it already reads score + feedback together.
 */
@Component
public class InterviewPerformanceTool {

    private static final int MAX_INTERVIEWS_SHOWN = 3;
    private static final int FEEDBACK_EXCERPT_CHARS = 300;

    private final InterviewService interviewService;

    public InterviewPerformanceTool(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @Tool("""
        Retrieve the candidate's past interview performance: previous round scores,
        completion status, and AI feedback text from earlier rounds (Aptitude,
        Communication, DSA, HR) across their interview history. Use this when the
        request is about the candidate's actual track record, weak areas from past
        attempts, or when building a preparation plan that should account for
        previously identified issues. Do not call this for a first-time candidate
        with no interview history — it will simply return that no history exists.
        """)
    public String getInterviewPerformance() {
        return ToolActivityRecorder.track("getInterviewPerformance", () -> {
            String username = AgentRequestContext.username();

            List<Interview> interviews = interviewService.getMyInterviews(username);
            if (interviews == null || interviews.isEmpty()) {
                return "No interview history found for this candidate yet.";
            }

            List<Interview> recent = interviews.stream()
                .sorted(Comparator.comparing(Interview::getId).reversed())
                .limit(MAX_INTERVIEWS_SHOWN)
                .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append("NOTE: this application's round scoring currently returns a FIXED number per ")
              .append("round type regardless of actual answer quality (a known placeholder in the ")
              .append("codebase) — treat the numeric scores below as low-signal. The AI FEEDBACK TEXT ")
              .append("per round is the real signal of strengths/weaknesses; base your analysis on that.\n\n");

            for (Interview interview : recent) {
                sb.append("=== Interview #").append(interview.getId())
                  .append(" — status: ").append(interview.getStatus())
                  .append(", total score: ").append(interview.getTotalScore())
                  .append(" ===\n");

                List<InterviewRound> rounds = interviewService.getRounds(interview.getId());
                if (rounds == null || rounds.isEmpty()) {
                    sb.append("(no rounds recorded)\n\n");
                    continue;
                }

                for (InterviewRound round : rounds) {
                    sb.append("- ").append(round.getRoundType())
                      .append(" [").append(round.getStatus()).append("]");

                    if (round.getScore() != null) {
                        sb.append(", score: ").append(round.getScore());
                    }
                    sb.append("\n");

                    String feedback = round.getAiFeedback();
                    if (feedback != null && !feedback.isBlank()) {
                        String excerpt = feedback.length() > FEEDBACK_EXCERPT_CHARS
                            ? feedback.substring(0, FEEDBACK_EXCERPT_CHARS) + "..."
                            : feedback;
                        sb.append("  feedback: ").append(excerpt.replace("\n", " ")).append("\n");
                    }
                }
                sb.append("\n");
            }

            return sb.toString();
        });
    }
}
