package com.example.AI_InterviewSystem.Service;

import com.example.AI_InterviewSystem.Dto.RoundSubmitRequest;
import com.example.AI_InterviewSystem.Model.*;
import com.example.AI_InterviewSystem.Repository.InterviewRepository;
import com.example.AI_InterviewSystem.Repository.InterviewRoundRepository;
import com.example.AI_InterviewSystem.Repository.UserRepo;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final InterviewRoundRepository roundRepository;
    private final UserRepo userRepo;
    private final ChatService chatService;

    public InterviewService(
            InterviewRepository interviewRepository,
            InterviewRoundRepository roundRepository,
            UserRepo userRepo,
            ChatService chatService
    ) {
        this.interviewRepository = interviewRepository;
        this.roundRepository = roundRepository;
        this.userRepo = userRepo;
        this.chatService = chatService;
    }

    // USER starts interview
    public Interview startInterview(String username) {

        Users user = userRepo.findByUsername(username);

        Interview interview = new Interview();
        interview.setUser(user);
        interview.setStatus(InterviewStatus.STARTED);

        Interview savedInterview = interviewRepository.save(interview);

        createRound(savedInterview, RoundType.APTITUDE);
        createRound(savedInterview, RoundType.COMMUNICATION);
        createRound(savedInterview, RoundType.DSA);
        createRound(savedInterview, RoundType.HR);

        return savedInterview;
    }

    private void createRound(Interview interview, RoundType roundType) {
        InterviewRound round = new InterviewRound();
        round.setInterview(interview);
        round.setRoundType(roundType);
        round.setStatus(RoundStatus.NOT_STARTED);
        roundRepository.save(round);
    }

    public List<Interview> getMyInterviews(String username) {
        Users user = userRepo.findByUsername(username);
        return interviewRepository.findByUser(user);
    }

    public List<Interview> getAllInterviews() {
        return interviewRepository.findAll();
    }

    public List<InterviewRound> getRounds(Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Interview not found"));

        return roundRepository.findByInterview(interview);
    }

    public InterviewRound startRound(Long roundId) {
        InterviewRound round = roundRepository.findById(roundId)
                .orElseThrow(() -> new RuntimeException("Round not found"));

        round.setStatus(RoundStatus.STARTED);
        round.setStartedAt(LocalDateTime.now());

        return roundRepository.save(round);
    }

    public InterviewRound submitRound(Long roundId, RoundSubmitRequest request) {

        InterviewRound round = roundRepository.findById(roundId)
                .orElseThrow(() -> new RuntimeException("Round not found"));

        round.setQuestion(request.getQuestion());
        round.setAnswer(request.getAnswer());
        round.setCode(request.getCode());
        round.setLanguage(request.getLanguage());

        String aiFeedback = generateFeedback(round.getRoundType(), request);

        round.setAiFeedback(aiFeedback);
        round.setScore(calculateScore(round.getRoundType(), aiFeedback));
        round.setStatus(RoundStatus.COMPLETED);
        round.setCompletedAt(LocalDateTime.now());

        InterviewRound savedRound = roundRepository.save(round);

        updateInterviewScore(round.getInterview());

        return savedRound;
    }

    private String generateFeedback(RoundType roundType, RoundSubmitRequest request) {

        if (roundType == RoundType.DSA) {
            return chatService.analyzeCode(
                    request.getCode(),
                    request.getLanguage(),
                    request.getQuestion()
            );
        }

        if (roundType == RoundType.APTITUDE) {
            return "Aptitude answer submitted successfully.";
        }

        String prompt = """
                You are an AI interviewer.
                Round: %s
                Question: %s
                Candidate Answer: %s

                Give feedback, mistakes, improvement tips, and score suggestion.
                """.formatted(roundType, request.getQuestion(), request.getAnswer());

        return chatService.ask(prompt);
    }

    private Integer calculateScore(RoundType roundType, String feedback) {

        // Simple temporary score logic
        // Later you can extract score from AI response
        if (roundType == RoundType.DSA) return 30;
        if (roundType == RoundType.APTITUDE) return 25;
        if (roundType == RoundType.COMMUNICATION) return 25;
        if (roundType == RoundType.HR) return 20;

        return 0;
    }

    private void updateInterviewScore(Interview interview) {

        List<InterviewRound> rounds = roundRepository.findByInterview(interview);

        int total = rounds.stream()
                .mapToInt(InterviewRound::getScore)
                .sum();

        interview.setTotalScore(total);

        boolean allCompleted = rounds.stream()
                .allMatch(round -> round.getStatus() == RoundStatus.COMPLETED);

        if (allCompleted) {
            interview.setStatus(InterviewStatus.COMPLETED);
            interview.setCompletedAt(LocalDateTime.now());
        }

        interviewRepository.save(interview);
    }

    public Interview getFinalResult(Long interviewId) {
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Interview not found"));
    }
}