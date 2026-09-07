package com.example.AI_InterviewSystem.Service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private final PdfEmbeddingService pdfEmbeddingService;
    private final ChatModel chatModel;

    public RagService(PdfEmbeddingService pdfEmbeddingService, ChatModel chatModel) {
        this.pdfEmbeddingService = pdfEmbeddingService;
        this.chatModel = chatModel;
    }

    // Retrieves the candidate's own resume chunks relevant to their question,
    // then asks Groq to answer using that context — a resume-aware, grounded response.
    public String answerWithResumeContext(String username, Long interviewId, String question) {

        if (question == null || question.isBlank()) {
            throw new RuntimeException("Question cannot be empty");
        }

        List<String> chunks = pdfEmbeddingService.retrieveRelevantChunks(username, interviewId, question, 5);

        String contextBlock = chunks.isEmpty()
                ? "No relevant resume context was found for this candidate."
                : String.join("\n---\n", chunks);

        String systemPrompt = """
                You are an expert technical interviewer conducting a resume-based interview.

                Use the candidate's resume context below to personalize your answer whenever it's relevant
                (e.g. referencing their actual projects, skills, or experience). If the resume context does
                not cover the question, answer generally and clearly note that it isn't based on their resume.

                Be concise, accurate, and interview-appropriate.
                """;

        String userMessage = """
                Resume Context:
                %s

                Candidate Question:
                %s
                """.formatted(contextBlock, question);

        try {
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userMessage)
            );

            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .temperature(0.3)
                    .maxOutputTokens(900)
                    .build();

            ChatResponse response = chatModel.chat(request);

            String content = response.aiMessage().text();

            if (content == null || content.isBlank()) {
                throw new RuntimeException("Empty response from Groq API");
            }

            return content;

        } catch (Exception e) {
            throw new RuntimeException("RAG answer generation failed: " + e.getMessage());
        }
    }
}