package com.example.AI_InterviewSystem.Configuration;

import com.example.AI_InterviewSystem.agent.InterviewPreparationAgent;
import com.example.AI_InterviewSystem.agent.tools.CandidateProfileTool;
import com.example.AI_InterviewSystem.agent.tools.CompanyRecommendationTool;
import com.example.AI_InterviewSystem.agent.tools.CompanyResearchTool;
import com.example.AI_InterviewSystem.agent.tools.GithubTool;
import com.example.AI_InterviewSystem.agent.tools.InterviewPerformanceTool;
import com.example.AI_InterviewSystem.agent.tools.JobDescriptionTool;
import com.example.AI_InterviewSystem.agent.tools.JobSearchTool;
import com.example.AI_InterviewSystem.agent.tools.PreparationPlannerTool;
import com.example.AI_InterviewSystem.agent.tools.ResumeRagTool;
import com.example.AI_InterviewSystem.agent.tools.SkillGapTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AgentConfig {

    @Value("${ollama.base-url}")
    private String baseUrl;

    @Value("${agent.ollama.model:qwen2.5:7b}")
    private String modelName;

    @Value("${agent.ollama.temperature:0.2}")
    private double temperature;

    // FIX (latency root cause): Ollama's default num_ctx is 2048 tokens. Your
    // system prompt + 10 tool schemas + growing tool-call/result history
    // regularly hits 3700-4100+ tokens by the 2nd-3rd round trip of a single
    // turn (confirmed from production prompt_eval_count logs). Once a request
    // exceeds num_ctx, Ollama has to shift/truncate context and reprocess —
    // this is the actual cause of prompt_eval_duration scaling into the
    // hundreds of seconds, not just "CPU inference is slow." It also silently
    // drops earlier messages (e.g. a verified GitHub username from 2 tool
    // calls ago), which is what the lastVerifiedGithubUsername cache in
    // AgentRequestContext was band-aiding around.
    //
    // 8192 gives comfortable headroom for the shortened system prompt (~1.2k
    // tokens incl. tool schemas) plus a full 8-12 call tool chain's worth of
    // results, without ever forcing a context shift mid-turn.
    //
    // VERIFY: .numCtx(int) against your exact langchain4j-ollama 1.10.0
    // Builder API before running — I can't execute your build to confirm the
    // method signature. If it's not present on OllamaChatModel.Builder in
    // your version, set it via .customParameters(Map.of("num_ctx", 8192))
    // or an equivalent options map instead.
    @Value("${agent.ollama.num-ctx:8192}")
    private int numCtx;

    @Bean
    public ChatModel agentChatModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .numCtx(numCtx)
                .timeout(Duration.ofSeconds(300))
                .numPredict(400)
                .maxRetries(0)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public InterviewPreparationAgent interviewPreparationAgent(
            ChatModel agentChatModel,
            ResumeRagTool resumeRagTool,
            JobDescriptionTool jobDescriptionTool,
            SkillGapTool skillGapTool,
            InterviewPerformanceTool interviewPerformanceTool,
            PreparationPlannerTool preparationPlannerTool,
            GithubTool githubTool,
            JobSearchTool jobSearchTool,
            CompanyResearchTool companyResearchTool,
            CandidateProfileTool candidateProfileTool,
            CompanyRecommendationTool companyRecommendationTool
    ) {
        return AiServices.builder(InterviewPreparationAgent.class)
                .chatModel(agentChatModel)
                .tools(
                        resumeRagTool,
                        jobDescriptionTool,
                        skillGapTool,
                        interviewPerformanceTool,
                        preparationPlannerTool,
                        githubTool,
                        jobSearchTool,
                        companyResearchTool,
                        candidateProfileTool,
                        companyRecommendationTool
                )
                // Kept at 20 (raised previously to fix a real eviction bug) —
                // now that num_ctx is wide enough to actually hold 20
                // messages without forced truncation, this number finally
                // means what it says.
                .chatMemoryProvider(sessionId -> MessageWindowChatMemory.withMaxMessages(20))
                .build();
    }
}