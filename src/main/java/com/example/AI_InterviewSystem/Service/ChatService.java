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
public class ChatService {

    private final ChatModel chatModel;

    public ChatService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    // Default Groq call
    private String callGroq(String systemPrompt, String userMessage) {
        return callGroq(systemPrompt, userMessage, 900);
    }

    // Controlled token Groq call
    private String callGroq(String systemPrompt, String userMessage, int maxTokens) {

        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new RuntimeException("System prompt is missing");
        }

        if (userMessage == null || userMessage.isBlank()) {
            throw new RuntimeException("User message cannot be empty");
        }

        try {
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userMessage)
            );

            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .temperature(0.2)
                    .maxOutputTokens(maxTokens)
                    .build();

            ChatResponse response = chatModel.chat(request);

            String content = response.aiMessage().text();

            if (content == null || content.isBlank()) {
                throw new RuntimeException("Empty response from Groq API");
            }

            return content;

        } catch (Exception e) {
            throw new RuntimeException("Something went wrong: " + e.getMessage());
        }
    }

    // Normal chat
    public String ask(String userMessage) {

        if (userMessage == null || userMessage.isBlank()) {
            throw new RuntimeException("Message cannot be empty");
        }

        String systemPrompt = """
                You are an expert AI assistant for software engineering interview preparation.
                Help users with technical questions, HR questions, coding problems, and career advice.
                Be concise, clear, and practical.
                """;

        return callGroq(systemPrompt, userMessage, 900);
    }

    // Short AI call for technical interview to avoid Groq rate limit
    public String askShort(String userMessage) {

        if (userMessage == null || userMessage.isBlank()) {
            throw new RuntimeException("Message cannot be empty");
        }

        String systemPrompt = """
                You are an expert technical interviewer.
                Be concise, strict, practical, and fresher-friendly.
                Keep responses short and structured.
                """;

        return callGroq(systemPrompt, userMessage, 700);
    }

    public String analyzeCode(String code, String language, String problemStatement) {

        if (code == null || code.isBlank()) {
            throw new RuntimeException("Code cannot be empty");
        }

        if (language == null || language.isBlank()) {
            language = "Java";
        }

        if (problemStatement == null || problemStatement.isBlank()) {
            problemStatement = "No problem statement provided";
        }

        String systemPrompt = """
                You are an expert code reviewer and algorithm analyst like LeetCode judge plus senior software engineer.

                Analyze the submitted code carefully.

                Return the answer in this EXACT format:

                ## Correctness
                - Say whether the code solves the problem correctly.
                - Mention logical mistakes if any.
                - Mention missed edge cases.

                ## Time Complexity
                - Best Case: O(?)
                - Average Case: O(?)
                - Worst Case: O(?)
                - Explanation: explain why this complexity happens.

                ## Space Complexity
                - O(?)
                - Explanation: explain extra memory usage.

                ## Errors and Issues
                - Compilation errors:
                - Runtime errors:
                - Logical errors:
                - Edge cases missed:

                ## Optimizations
                - Explain if the solution can be improved.
                - Mention better data structures or algorithms.

                ## Optimized Code
                Provide optimized code in the same programming language.

                ## Overall Rating
                - Type: Brute Force / Acceptable / Optimal
                - Score: X/10

                Important rules:
                - Do not skip Time Complexity.
                - Do not skip Space Complexity.
                - Do not give vague answers.
                - If code is wrong, clearly explain why.
                - If problem statement is missing, still analyze the code generally.
                """;

        String userMessage = """
                Language: %s

                Problem Statement:
                %s

                Code:
                ```%s
                %s
                ```
                """.formatted(language, problemStatement, language.toLowerCase(), code);

        return callGroq(systemPrompt, userMessage, 1400);
    }

    public String generateInterviewQuestion(String topic, String difficulty, String language) {

        if (topic == null || topic.isBlank()) {
            topic = "Data Structures";
        }

        if (difficulty == null || difficulty.isBlank()) {
            difficulty = "Medium";
        }

        if (language == null || language.isBlank()) {
            language = "Java";
        }

        String systemPrompt = """
                You are an expert technical interviewer.
                Generate coding interview questions like LeetCode.

                Return in this format:

                ## Problem Title
                ## Difficulty
                ## Problem Statement
                ## Input Format
                ## Output Format
                ## Examples
                ## Constraints
                ## Hints
                ## Tags
                """;

        String userMessage = "Generate a %s difficulty interview question on topic: %s. Language: %s"
                .formatted(difficulty, topic, language);

        return callGroq(systemPrompt, userMessage, 900);
    }

    public String generateAptitudeQuestions() {

        String systemPrompt = """
                You are an expert aptitude test generator.

                Generate 10 multiple choice aptitude questions.

                Return ONLY valid JSON array. No extra text.

                Format:

                [
                  {
                    "question": "Question text",
                    "options": ["Option A", "Option B", "Option C", "Option D"],
                    "answer": 0
                  }
                ]

                Rules:
                - Exactly 10 questions
                - Answer must be index 0 to 3
                - Questions should be logical, numerical, reasoning based
                - No explanation
                - No markdown
                """;

        String userMessage = "Generate aptitude questions";

        return callGroq(systemPrompt, userMessage, 900);
    }

    public String getHint(String problem, String currentCode) {

        if (problem == null || problem.isBlank()) {
            throw new RuntimeException("Problem cannot be empty");
        }

        if (currentCode == null) {
            currentCode = "";
        }

        String systemPrompt = """
                You are a helpful coding mentor.
                Give hints without revealing full solution code.

                Return in this format:

                ## Hint
                ## Think About
                ## Approach Direction
                """;

        String userMessage = """
                Problem:
                %s

                Current Code:
                %s
                """.formatted(problem, currentCode.isBlank() ? "No code written yet." : currentCode);

        return callGroq(systemPrompt, userMessage, 700);
    }
}