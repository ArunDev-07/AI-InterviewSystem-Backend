package com.example.AI_InterviewSystem.Service;

import com.example.AI_InterviewSystem.Dto.TechnicalInterviewAnswerRequest;
import com.example.AI_InterviewSystem.Dto.TechnicalInterviewQuestionDto;
import com.example.AI_InterviewSystem.Model.TechnicalInterview;
import com.example.AI_InterviewSystem.Model.TechnicalInterviewAnswer;
import com.example.AI_InterviewSystem.Repository.TechnicalInterviewAnswerRepository;
import com.example.AI_InterviewSystem.Repository.TechnicalInterviewRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TechnicalInterviewService {

    private final TechnicalInterviewRepository interviewRepository;
    private final TechnicalInterviewAnswerRepository answerRepository;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public TechnicalInterviewService(
            TechnicalInterviewRepository interviewRepository,
            TechnicalInterviewAnswerRepository answerRepository,
            ChatService chatService,
            ObjectMapper objectMapper
    ) {
        this.interviewRepository = interviewRepository;
        this.answerRepository = answerRepository;
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    // Extract text from uploaded PDF resume
    public String extractResumeText(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                throw new RuntimeException("Resume file is empty");
            }

            String fileName = Optional.ofNullable(file.getOriginalFilename())
                    .orElse("")
                    .toLowerCase();

            if (!fileName.endsWith(".pdf")) {
                throw new RuntimeException("Only PDF resume upload is supported now");
            }

            try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);

                if (text == null || text.isBlank()) {
                    throw new RuntimeException("Could not extract text from resume");
                }

                return cleanResumeText(text);
            }

        } catch (Exception e) {
            throw new RuntimeException("Resume extraction failed: " + e.getMessage());
        }
    }

    // Start interview and generate first set of resume-based questions
    public Map<String, Object> startInterview(String username, String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            throw new RuntimeException("Resume text cannot be empty");
        }

        String prompt = """
                Generate exactly 5 technical interview questions based on this resume.

                Resume:
                %s

                Rules:
                - Ask only from resume skills and projects.
                - Focus on Java, Spring Boot, React, MySQL, REST API, JWT, DSA if present.
                - Fresher-level but technical.
                - Do not ask HR questions.
                - Do not ask aptitude questions.
                - Return ONLY valid JSON array.
                - No markdown.
                - No explanation.

                JSON format:
                [
                  {
                    "id": 1,
                    "question": "Explain how JWT authentication works in your Spring Boot project.",
                    "topic": "Spring Security",
                    "difficulty": "Medium"
                  }
                ]
                """.formatted(limitText(resumeText, 2500));

        List<TechnicalInterviewQuestionDto> questions;

        try {
            String aiResponse = chatService.askShort(prompt);
            questions = parseQuestions(aiResponse);

            if (questions.isEmpty()) {
                questions = fallbackQuestions();
            }

        } catch (Exception e) {
            questions = fallbackQuestions();
        }

        String questionsJson;

        try {
            questionsJson = objectMapper.writeValueAsString(questions);
        } catch (Exception e) {
            questionsJson = "[]";
        }

        TechnicalInterview interview = new TechnicalInterview();
        interview.setUsername(username);
        interview.setResumeText(limitText(resumeText, 5000));
        interview.setQuestionsJson(questionsJson);
        interview.setTotalScore(0);
        interview.setStatus("STARTED");
        interview.setStartedAt(LocalDateTime.now());

        TechnicalInterview saved = interviewRepository.save(interview);

        return Map.of(
                "interviewId", saved.getId(),
                "questions", questions,
                "message", "Technical interview started successfully"
        );
    }

    // Submit answer and evaluate using AI
    public Map<String, Object> submitAnswer(String username, TechnicalInterviewAnswerRequest request) {
        if (request.getInterviewId() == null) {
            throw new RuntimeException("Interview ID is required");
        }

        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new RuntimeException("Question cannot be empty");
        }

        if (request.getAnswer() == null || request.getAnswer().isBlank()) {
            throw new RuntimeException("Answer cannot be empty");
        }

        TechnicalInterview interview = interviewRepository.findById(request.getInterviewId())
                .orElseThrow(() -> new RuntimeException("Technical interview not found"));

        if (!interview.getUsername().equals(username)) {
            throw new RuntimeException("You are not allowed to submit answer for this interview");
        }

        String prompt = """
                Evaluate this technical interview answer.

                Resume Context:
                %s

                Question:
                %s

                Candidate Answer:
                %s

                Topic:
                %s

                Return in this exact short format:

                ## Score
                X/10

                ## Feedback
                - Correct points:
                - Mistakes:
                - Missing points:
                - Clarity:

                ## Optimal Answer
                Give the best fresher-friendly technical answer.

                ## Follow-up Question
                Ask one relevant follow-up question.

                Rules:
                - Be strict but helpful.
                - Score must be 0/10 to 10/10.
                - Keep answer concise.
                """.formatted(
                limitText(interview.getResumeText(), 2500),
                request.getQuestion(),
                limitText(request.getAnswer(), 1200),
                request.getTopic() == null ? "General Technical" : request.getTopic()
        );

        String aiFeedback;

        try {
            aiFeedback = chatService.askShort(prompt);
        } catch (Exception e) {
            aiFeedback = fallbackAnswerFeedback();
        }

        int score = extractScore(aiFeedback);
        String optimalAnswer = extractSection(aiFeedback, "Optimal Answer");

        TechnicalInterviewAnswer answer = new TechnicalInterviewAnswer();
        answer.setInterviewId(interview.getId());
        answer.setUsername(username);
        answer.setQuestion(request.getQuestion());
        answer.setAnswer(request.getAnswer());
        answer.setTopic(request.getTopic());
        answer.setAiFeedback(aiFeedback);
        answer.setOptimalAnswer(optimalAnswer);
        answer.setScore(score);
        answer.setCreatedAt(LocalDateTime.now());

        TechnicalInterviewAnswer saved = answerRepository.save(answer);

        updateInterviewScore(interview.getId());

        return Map.of(
                "score", score,
                "feedback", aiFeedback,
                "optimalAnswer", optimalAnswer,
                "data", saved
        );
    }

    // Generate next real interview question based on previous answer
    public Map<String, Object> generateNextQuestion(
            String username,
            Long interviewId,
            String previousQuestion,
            String previousAnswer,
            String previousFeedback
    ) {
        if (interviewId == null) {
            throw new RuntimeException("Interview ID is required");
        }

        TechnicalInterview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Technical interview not found"));

        if (!interview.getUsername().equals(username)) {
            throw new RuntimeException("You are not allowed to access this interview");
        }

        List<TechnicalInterviewAnswer> answers =
                answerRepository.findByInterviewIdOrderByIdAsc(interviewId);

        if (answers.size() >= 5) {
            return Map.of(
                    "completed", true,
                    "message", "Maximum technical interview questions completed"
            );
        }

        String prompt = """
                You are a real technical interviewer.

                Resume Context:
                %s

                Previous Question:
                %s

                Candidate Answer:
                %s

                Previous AI Feedback:
                %s

                Already Asked Questions:
                %s

                Generate ONE next technical interview question.

                Rules:
                - Ask like a real interviewer.
                - Ask based on resume and previous answer.
                - Prefer follow-up questions if the previous answer was weak or incomplete.
                - Do not repeat already asked questions.
                - Focus on Java, Spring Boot, React, MySQL, JWT, REST API, DSA, project architecture if present.
                - Return ONLY valid JSON object.
                - No markdown.
                - No explanation.

                JSON format:
                {
                  "id": 2,
                  "question": "How does your JwtFilter validate the token before allowing protected API access?",
                  "topic": "Spring Security",
                  "difficulty": "Medium"
                }
                """.formatted(
                limitText(interview.getResumeText(), 2500),
                previousQuestion == null ? "No previous question" : previousQuestion,
                previousAnswer == null ? "No previous answer" : limitText(previousAnswer, 1200),
                previousFeedback == null ? "No previous feedback" : limitText(previousFeedback, 1200),
                limitText(buildAskedQuestionsText(answers), 1800)
        );

        TechnicalInterviewQuestionDto nextQuestion;

        try {
            String aiResponse = chatService.askShort(prompt);
            nextQuestion = parseSingleQuestion(aiResponse, answers.size() + 1);
        } catch (Exception e) {
            nextQuestion = fallbackNextQuestion(answers.size() + 1);
        }

        return Map.of(
                "completed", false,
                "question", nextQuestion
        );
    }

    // Get result
    public Map<String, Object> getResult(String username, Long interviewId) {
        TechnicalInterview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Technical interview not found"));

        if (!interview.getUsername().equals(username)) {
            throw new RuntimeException("You are not allowed to view this interview");
        }

        List<TechnicalInterviewAnswer> answers =
                answerRepository.findByInterviewIdOrderByIdAsc(interviewId);

        return Map.of(
                "interview", interview,
                "answers", answers,
                "totalScore", interview.getTotalScore() == null ? 0 : interview.getTotalScore(),
                "status", interview.getStatus() == null ? "STARTED" : interview.getStatus()
        );
    }

    // Get user interview history
    public List<TechnicalInterview> getMyInterviews(String username) {
        return interviewRepository.findByUsernameOrderByStartedAtDesc(username);
    }

    // Complete interview and create final report
    public Map<String, Object> completeInterview(String username, Long interviewId) {
        TechnicalInterview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Technical interview not found"));

        if (!interview.getUsername().equals(username)) {
            throw new RuntimeException("You are not allowed to complete this interview");
        }

        updateInterviewScore(interviewId);

        interview.setStatus("COMPLETED");
        interview.setCompletedAt(LocalDateTime.now());

        TechnicalInterview saved = interviewRepository.save(interview);

        List<TechnicalInterviewAnswer> answers =
                answerRepository.findByInterviewIdOrderByIdAsc(interviewId);

        String reportPrompt = """
                Generate a short final technical interview report.

                Resume:
                %s

                Answers:
                %s

                Return in this format:

                ## Final Summary
                ## Strong Areas
                ## Weak Areas
                ## Technical Mistakes
                ## Preparation Plan
                ## Final Recommendation

                Keep it concise for a fresher Java Full Stack Developer.
                """.formatted(
                limitText(saved.getResumeText(), 2500),
                limitText(buildAnswersText(answers), 3000)
        );

        String finalReport;

        try {
            finalReport = chatService.askShort(reportPrompt);
        } catch (Exception e) {
            finalReport = fallbackFinalReport();
        }

        return Map.of(
                "interview", saved,
                "answers", answers,
                "finalReport", finalReport
        );
    }

    private void updateInterviewScore(Long interviewId) {
        TechnicalInterview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Technical interview not found"));

        List<TechnicalInterviewAnswer> answers =
                answerRepository.findByInterviewIdOrderByIdAsc(interviewId);

        int total = answers.stream()
                .mapToInt(answer -> answer.getScore() == null ? 0 : answer.getScore())
                .sum();

        interview.setTotalScore(total);
        interviewRepository.save(interview);
    }

    private List<TechnicalInterviewQuestionDto> parseQuestions(String aiResponse) {
        try {
            String json = extractJsonArray(aiResponse);

            return objectMapper.readValue(
                    json,
                    new TypeReference<List<TechnicalInterviewQuestionDto>>() {}
            );

        } catch (Exception e) {
            return fallbackQuestions();
        }
    }

    private TechnicalInterviewQuestionDto parseSingleQuestion(String aiResponse, int fallbackId) {
        try {
            String json = extractJsonObject(aiResponse);

            TechnicalInterviewQuestionDto question =
                    objectMapper.readValue(json, TechnicalInterviewQuestionDto.class);

            if (question.getId() == null) {
                question.setId(fallbackId);
            }

            if (question.getQuestion() == null || question.getQuestion().isBlank()) {
                return fallbackNextQuestion(fallbackId);
            }

            if (question.getTopic() == null || question.getTopic().isBlank()) {
                question.setTopic("Technical");
            }

            if (question.getDifficulty() == null || question.getDifficulty().isBlank()) {
                question.setDifficulty("Medium");
            }

            return question;

        } catch (Exception e) {
            return fallbackNextQuestion(fallbackId);
        }
    }

    private String extractJsonArray(String text) {
        if (text == null) return "[]";

        int start = text.indexOf("[");
        int end = text.lastIndexOf("]");

        if (start == -1 || end == -1 || end <= start) {
            return "[]";
        }

        return text.substring(start, end + 1);
    }

    private String extractJsonObject(String text) {
        if (text == null) return "{}";

        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");

        if (start == -1 || end == -1 || end <= start) {
            return "{}";
        }

        return text.substring(start, end + 1);
    }

    private List<TechnicalInterviewQuestionDto> fallbackQuestions() {
        return List.of(
                new TechnicalInterviewQuestionDto(
                        1,
                        "Explain your AI Interview System project architecture.",
                        "Project Architecture",
                        "Medium"
                ),
                new TechnicalInterviewQuestionDto(
                        2,
                        "How does JWT authentication work in your Spring Boot project?",
                        "Spring Security",
                        "Medium"
                ),
                new TechnicalInterviewQuestionDto(
                        3,
                        "Explain Controller, Service, and Repository layers in Spring Boot.",
                        "Spring Boot",
                        "Easy"
                ),
                new TechnicalInterviewQuestionDto(
                        4,
                        "How do React and Spring Boot communicate in your full-stack projects?",
                        "Full Stack",
                        "Easy"
                ),
                new TechnicalInterviewQuestionDto(
                        5,
                        "How did you design and use MySQL tables in your projects?",
                        "MySQL",
                        "Medium"
                )
        );
    }

    private TechnicalInterviewQuestionDto fallbackNextQuestion(int id) {
        List<TechnicalInterviewQuestionDto> fallback = List.of(
                new TechnicalInterviewQuestionDto(
                        id,
                        "Explain one challenging feature you implemented in your AI Interview System project.",
                        "Project Architecture",
                        "Medium"
                ),
                new TechnicalInterviewQuestionDto(
                        id,
                        "How does JWT authentication work in your Spring Boot backend?",
                        "Spring Security",
                        "Medium"
                ),
                new TechnicalInterviewQuestionDto(
                        id,
                        "Explain how React frontend communicates with Spring Boot REST APIs.",
                        "Full Stack",
                        "Easy"
                ),
                new TechnicalInterviewQuestionDto(
                        id,
                        "How did you design MySQL tables for your full-stack projects?",
                        "MySQL",
                        "Medium"
                ),
                new TechnicalInterviewQuestionDto(
                        id,
                        "Explain Controller, Service, and Repository layers in Spring Boot.",
                        "Spring Boot",
                        "Easy"
                )
        );

        int index = Math.max(0, Math.min(id - 1, fallback.size() - 1));
        return fallback.get(index);
    }

    private String fallbackAnswerFeedback() {
        return """
                ## Score
                5/10

                ## Feedback
                - Correct points: Your answer was submitted successfully.
                - Mistakes: AI evaluation could not be completed because Groq limit or backend AI error happened.
                - Missing points: Add implementation details, examples, and technical keywords.
                - Clarity: Try to answer in a structured way.

                ## Optimal Answer
                A strong answer should explain the concept clearly, connect it to your project, mention the tools used, and include one real implementation example.

                ## Follow-up Question
                Can you explain this with one real example from your project?
                """;
    }

    private String fallbackFinalReport() {
        return """
                ## Final Summary
                Technical interview completed.

                ## Strong Areas
                Review your saved answer scores and improve the strong topics.

                ## Weak Areas
                Improve unclear or low-scoring technical answers.

                ## Technical Mistakes
                Check missing concepts, weak explanation, and lack of project examples.

                ## Preparation Plan
                Revise Java, Spring Boot, REST API, JWT, MySQL, React, and DSA.

                ## Final Recommendation
                Practice explaining your projects clearly with real implementation details.
                """;
    }

    private int extractScore(String aiResponse) {
        try {
            Pattern pattern = Pattern.compile("(\\d+)\\s*/\\s*10");
            Matcher matcher = pattern.matcher(aiResponse);

            if (matcher.find()) {
                int score = Integer.parseInt(matcher.group(1));
                return Math.max(0, Math.min(10, score));
            }
        } catch (Exception ignored) {
        }

        return 0;
    }

    private String extractSection(String text, String sectionName) {
        if (text == null || text.isBlank()) return "";

        String regex = "##\\s*" + Pattern.quote(sectionName) + "\\s*([\\s\\S]*?)(?=\\n##\\s|$)";

        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return "";
    }

    private String cleanResumeText(String text) {
        return text
                .replaceAll("\\s+", " ")
                .replaceAll("[^\\x20-\\x7E]", " ")
                .trim();
    }

    private String limitText(String text, int maxChars) {
        if (text == null) return "";

        if (text.length() <= maxChars) return text;

        return text.substring(0, maxChars);
    }

    private String buildAskedQuestionsText(List<TechnicalInterviewAnswer> answers) {
        StringBuilder sb = new StringBuilder();

        for (TechnicalInterviewAnswer answer : answers) {
            sb.append("- ")
                    .append(answer.getQuestion() == null ? "" : answer.getQuestion())
                    .append("\n");
        }

        return sb.toString();
    }

    private String buildAnswersText(List<TechnicalInterviewAnswer> answers) {
        StringBuilder sb = new StringBuilder();

        for (TechnicalInterviewAnswer answer : answers) {
            sb.append("Question: ")
                    .append(answer.getQuestion() == null ? "" : answer.getQuestion())
                    .append("\n");

            sb.append("Answer: ")
                    .append(answer.getAnswer() == null ? "" : answer.getAnswer())
                    .append("\n");

            sb.append("Score: ")
                    .append(answer.getScore() == null ? 0 : answer.getScore())
                    .append("/10\n");

            sb.append("Feedback: ")
                    .append(answer.getAiFeedback() == null ? "" : answer.getAiFeedback())
                    .append("\n\n");
        }

        return sb.toString();
    }
}