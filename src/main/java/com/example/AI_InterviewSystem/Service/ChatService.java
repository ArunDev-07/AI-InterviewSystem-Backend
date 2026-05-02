package com.example.AI_InterviewSystem.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ChatService {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ChatService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // Default Groq call
    private String callGroq(String systemPrompt, String userMessage) {
        return callGroq(systemPrompt, userMessage, 900);
    }

    // Controlled token Groq call
    private String callGroq(String systemPrompt, String userMessage, int maxTokens) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("Groq API key is missing");
        }

        if (apiUrl == null || apiUrl.isBlank()) {
            throw new RuntimeException("Groq API URL is missing");
        }

        if (model == null || model.isBlank()) {
            throw new RuntimeException("Groq model is missing");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.2);
        requestBody.put("max_tokens", maxTokens);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(apiUrl, request, String.class);

            if (response.getBody() == null) {
                throw new RuntimeException("Empty response from Groq API");
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            JsonNode contentNode = root
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content");

            if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                throw new RuntimeException("Invalid Groq API response");
            }

            return contentNode.asText();

        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Groq API Error: " + e.getResponseBodyAsString());
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