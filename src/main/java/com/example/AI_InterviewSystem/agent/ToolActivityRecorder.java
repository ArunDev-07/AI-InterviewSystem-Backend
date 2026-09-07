package com.example.AI_InterviewSystem.agent;

import com.example.AI_InterviewSystem.Dto.ToolExecutionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class ToolActivityRecorder {

    private static final Logger log = LoggerFactory.getLogger(ToolActivityRecorder.class);
    private static final ThreadLocal<List<ToolExecutionEvent>> EVENTS =
            ThreadLocal.withInitial(ArrayList::new);

    // FIX (2026-08-31): observed in production — the model re-calling
    // searchResume / getCandidateProfile / analyzeGithub / recommendCompanies
    // with the SAME arguments multiple times in one turn instead of moving
    // forward to saveCandidateProfile, burning tool-call budget and Ollama
    // round-trips (a direct driver of multi-minute turn latency) without ever
    // reaching the user's actual request. These four are read-only and
    // idempotent within a single turn — a repeat with identical input can
    // never return new information, so it's always safe to short-circuit.
    // Deliberately NOT applied to write/side-effecting tools (saveCandidateProfile)
    // or tools whose output can legitimately differ per call even with similar
    // input (searchJobs, researchCompany, analyzeSkillGap, createPreparationPlan).
    private static final Set<String> DEDUP_ELIGIBLE_TOOLS = Set.of(
            "searchResume", "getCandidateProfile", "analyzeGithub", "recommendCompanies"
    );
    private static final ThreadLocal<Map<String, Integer>> CALL_SIGNATURES =
            ThreadLocal.withInitial(HashMap::new);

    // FIX (2026-08-31): raised 8 -> 12. The richest legitimate chain today —
    // getCandidateProfile, searchResume, analyzeJobDescription, analyzeGithub,
    // analyzeSkillGap, createPreparationPlan, saveCandidateProfile,
    // recommendCompanies — is already 8 calls with ZERO room for a single
    // stumble (e.g. one placeholder-username retry). Combined with the
    // AgentRequestContext GitHub-username cache fix, a stumble now resolves in
    // one extra call instead of a multi-call retry spiral, so 12 is enough
    // headroom without inviting runaway looping.
    private static final int MAX_TOOL_CALLS_PER_TURN = 12;
    private static final int PREVIEW_MAX_CHARS = 150;

    private ToolActivityRecorder() {}

    public static String track(String toolName, String inputsForPreview, Supplier<String> action) {
        List<ToolExecutionEvent> events = EVENTS.get();

        if (DEDUP_ELIGIBLE_TOOLS.contains(toolName)) {
            String signature = toolName + "::" + normalizeSignature(inputsForPreview);
            Map<String, Integer> signatures = CALL_SIGNATURES.get();
            int priorCalls = signatures.getOrDefault(signature, 0);
            signatures.put(signature, priorCalls + 1);

            if (priorCalls >= 1) {
                String msg = "You already called '" + toolName + "' with this exact input earlier in this " +
                        "turn — calling it again cannot return new information, so it was skipped. Do NOT call " +
                        "'" + toolName + "' again with the same input. Use the result you already have: if you " +
                        "now have real findings from searchResume and/or analyzeGithub, call saveCandidateProfile " +
                        "with them next, then continue with the candidate's actual request instead of re-fetching " +
                        "data you already retrieved.";
                events.add(new ToolExecutionEvent(toolName, "SKIPPED_DUPLICATE", 0, null, truncate(inputsForPreview)));
                log.warn("Agent tool '{}' skipped — duplicate call with same input already made this turn", toolName);
                return msg;
            }
        }

        if (events.size() >= MAX_TOOL_CALLS_PER_TURN) {
            String msg = "Tool call limit (" + MAX_TOOL_CALLS_PER_TURN + " per turn) reached — " +
                    "answer with the information already gathered instead of calling more tools.";
            events.add(new ToolExecutionEvent(toolName, "SKIPPED_LIMIT", 0, null, truncate(inputsForPreview)));
            log.warn("Agent tool '{}' skipped — call limit of {} reached", toolName, MAX_TOOL_CALLS_PER_TURN);
            return msg;
        }

        long start = System.currentTimeMillis();
        try {
            String result = action.get();
            long duration = System.currentTimeMillis() - start;
            events.add(new ToolExecutionEvent(toolName, "SUCCESS", duration, null, truncate(inputsForPreview)));
            log.info("Agent tool '{}' succeeded in {}ms — input: {}", toolName, duration, truncate(inputsForPreview));
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            events.add(new ToolExecutionEvent(toolName, "FAILED", duration, e.getMessage(), truncate(inputsForPreview)));
            log.warn("Agent tool '{}' failed after {}ms: {}", toolName, duration, e.getMessage());
            return "Tool '" + toolName + "' failed and returned no data: " + e.getMessage();
        }
    }

    public static String track(String toolName, Supplier<String> action) {
        return track(toolName, null, action);
    }

    private static String normalizeSignature(String inputsForPreview) {
        if (inputsForPreview == null || inputsForPreview.isBlank()) return "(no args)";
        return inputsForPreview.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String s) {
        if (s == null || s.isBlank()) return "(no args)";
        String oneLine = s.replace("\n", " ").trim();
        return oneLine.length() > PREVIEW_MAX_CHARS ? oneLine.substring(0, PREVIEW_MAX_CHARS) + "..." : oneLine;
    }

    public static List<ToolExecutionEvent> drain() {
        List<ToolExecutionEvent> events = new ArrayList<>(EVENTS.get());
        EVENTS.remove();
        CALL_SIGNATURES.remove();
        return Collections.unmodifiableList(events);
    }

    public static void clear() {
        EVENTS.remove();
        CALL_SIGNATURES.remove();
    }
}