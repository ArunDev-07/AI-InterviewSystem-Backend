package com.example.AI_InterviewSystem.agent.tools;

import com.example.AI_InterviewSystem.Service.CompanyResearchService;
import com.example.AI_InterviewSystem.agent.ToolActivityRecorder;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Wraps CompanyResearchService — real web search via Tavily. Explicitly the
 * most optional tool in the system per the original spec ("keep this tool
 * modular and optional") — most requests won't need it, and it should only
 * fire when the candidate names a specific company.
 */
@Component
public class CompanyResearchTool {

    private static final int MAX_SOURCES_SHOWN = 3;

    private final CompanyResearchService companyResearchService;

    public CompanyResearchTool(CompanyResearchService companyResearchService) {
        this.companyResearchService = companyResearchService;
    }

    @Tool("""
        Research a specific company: what they do, their technology stack, and
        general interview process, using current web information. Use this ONLY
        when the candidate names a specific company they're interviewing with or
        asks about — never call this for a generic role/skills question. Pass just
        the company name (e.g. "Kyndryl"), not a full sentence.
        """)
    public String researchCompany(String companyName) {
        String preview = "companyName=" + companyName;
        return ToolActivityRecorder.track("researchCompany", preview, () -> {
            CompanyResearchService.CompanyResearchResult result;
            try {
                result = companyResearchService.researchCompany(companyName);
            } catch (CompanyResearchService.TavilyNotConfiguredException e) {
                return "Company research isn't configured (missing Tavily API key). " +
                        "Proceed without it and mention this limitation to the candidate.";
            } catch (CompanyResearchService.TavilyRateLimitException e) {
                return "Company research API rate limit was hit, so live results aren't " +
                        "available right now. Proceed without it and mention this limitation.";
            } catch (Exception e) {
                return "Company research failed unexpectedly (" + e.getMessage() + "). " +
                        "Proceed without it and mention this limitation to the candidate.";
            }

            if (result.answer() == null && result.sources().isEmpty()) {
                return "No current information found for '" + companyName + "'.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("COMPANY RESEARCH: ").append(companyName).append("\n\n");

            if (result.answer() != null && !result.answer().isBlank()) {
                sb.append("SUMMARY: ").append(result.answer()).append("\n\n");
            }

            List<CompanyResearchService.SourceSnippet> sources = result.sources();
            if (!sources.isEmpty()) {
                sb.append("SOURCES:\n");
                int shown = 0;
                for (CompanyResearchService.SourceSnippet source : sources) {
                    if (shown >= MAX_SOURCES_SHOWN) break;
                    sb.append("- ").append(source.title() == null ? "Untitled" : source.title());
                    if (source.url() != null) sb.append(" (").append(source.url()).append(")");
                    sb.append("\n");
                    shown++;
                }
            }

            return sb.toString();
        });
    }
}