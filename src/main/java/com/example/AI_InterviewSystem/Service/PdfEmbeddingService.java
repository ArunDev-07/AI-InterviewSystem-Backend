package com.example.AI_InterviewSystem.Service;

import com.example.AI_InterviewSystem.Exception.EmbeddingException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PdfEmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final ChromaService chromaService;

    public PdfEmbeddingService(EmbeddingModel embeddingModel, ChromaService chromaService) {
        this.embeddingModel = embeddingModel;
        this.chromaService = chromaService;
    }

    // Splits resume text into chunks, embeds each one, and stores them in ChromaDB
    // tagged with username + interviewId so retrieval can be scoped to this user's own resume.
    public void ingestResume(String username, Long interviewId, String resumeText) {

        if (resumeText == null || resumeText.isBlank()) {
            throw new EmbeddingException("Resume text cannot be empty");
        }

        Document document = Document.from(resumeText);

        // Resume chunks tend to read better a little larger than generic text (600 vs 500 chars)
        DocumentSplitter splitter = DocumentSplitters.recursive(600, 100);
        List<TextSegment> segments = splitter.split(document);

        if (segments.isEmpty()) {
            throw new EmbeddingException("Resume produced no chunks to embed");
        }

        List<String> ids = new ArrayList<>();
        List<List<Float>> vectors = new ArrayList<>();
        List<String> documents = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);

            Embedding embedding;
            try {
                embedding = embeddingModel.embed(segment).content();
            } catch (Exception e) {
                throw new EmbeddingException("Failed to generate embedding for chunk " + i, e);
            }

            List<Float> vector = embedding.vectorAsList();

            String chunkId = "resume-%d-%s-%d-%s".formatted(
                    interviewId,
                    username,
                    i,
                    UUID.randomUUID()
            );

            ids.add(chunkId);
            vectors.add(vector);
            documents.add(segment.text());
            metadatas.add(Map.of(
                    "username", username,
                    "interviewId", interviewId,
                    "chunkIndex", i
            ));
        }

        chromaService.addEmbeddings(ids, vectors, documents, metadatas);
    }

    // Embeds a candidate's question and retrieves the top matching resume chunks,
    // scoped to only this user's own interview so no cross-user data leaks through.
    //
    // NOTE: this is called mid-interview from submitAnswer() / generateNextQuestion(), so it
    // must fail SOFT. If Chroma is unreachable or the query otherwise fails, we log it and
    // return an empty list instead of throwing — the caller then falls back to "no resume
    // context" and the interview can continue instead of the whole request 400-ing.
    public List<String> retrieveRelevantChunks(String username, Long interviewId, String question, int topN) {

        if (question == null || question.isBlank()) {
            return List.of();
        }

        Embedding queryEmbedding;
        try {
            queryEmbedding = embeddingModel.embed(question).content();
        } catch (Exception e) {
            System.err.println("Embedding generation failed, continuing without resume context: " + e.getMessage());
            return List.of();
        }

        List<Float> vector = queryEmbedding.vectorAsList();

        Map<String, Object> whereFilter = Map.of(
                "username", username,
                "interviewId", interviewId
        );

        List<String> chunks;
        try {
            chunks = chromaService.queryTopChunks(vector, topN, whereFilter);
        } catch (Exception e) {
            System.err.println("Chroma query failed, continuing without resume context: " + e.getMessage());
            return List.of();
        }

        System.out.println("Retrieved " + chunks.size() + " chunks for query: " + question);
        chunks.forEach(chunk -> System.out.println(" - " + chunk));

        return chunks;
    }
}