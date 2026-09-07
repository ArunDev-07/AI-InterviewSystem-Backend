package com.example.AI_InterviewSystem.agent.tools;

import java.util.List;

/**
 * Shared vocabulary of recognizable technical skills/technologies. Used to
 * sanity-check inputs that are SUPPOSED to be skill lists but might actually
 * be something else (a GitHub username, a job title) that a smaller model
 * substituted by mistake — e.g. the production incident where analyzeSkillGap
 * was called with candidateSkills="ArunDev-07" and
 * requiredSkills="backend developer role".
 *
 * Not exhaustive by design — it only needs enough coverage that a genuine
 * skill list scores at least one hit against it.
 */
public final class TechKeywords {

    public static final List<String> KNOWN_TECH_KEYWORDS = List.of(
            "Java", "Spring Boot", "Spring", "Hibernate", "React", "Angular", "Vue",
            "Node.js", "Express", "TypeScript", "JavaScript", "Python", "Django", "Flask",
            "MySQL", "PostgreSQL", "MongoDB", "Redis", "Kafka", "RabbitMQ",
            "Docker", "Kubernetes", "AWS", "Azure", "GCP", "CI/CD", "Jenkins",
            "Microservices", "REST", "GraphQL", "gRPC", "JWT", "OAuth",
            "Git", "Maven", "Gradle", "JUnit", "Mockito", "Selenium",
            "HTML", "CSS", "Tailwind", "Bootstrap", "Nginx", "Terraform",
            "ChromaDB", "LangChain", "LLM", "RAG", "Machine Learning", "Deep Learning",
            "DSA", "Data Structures", "Algorithms", "System Design"
    );

    private TechKeywords() {}
}