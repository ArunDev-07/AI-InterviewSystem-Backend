package com.example.AI_InterviewSystem.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChromaService {

    private final RestClient chromaRestClient;

    @Value("${chroma.tenant}")
    private String tenant;

    @Value("${chroma.database}")
    private String database;

    @Value("${chroma.collection-name}")
    private String collectionName;

    // Cached after first lookup/creation so we don't hit Chroma on every call
    private String collectionId;

    public ChromaService(RestClient chromaRestClient) {
        this.chromaRestClient = chromaRestClient;
    }

    // Gets the collection id, creating the collection first if it doesn't exist yet
    public synchronized String getOrCreateCollectionId() {

        if (collectionId != null) {
            return collectionId;
        }

        String path = "/api/v2/tenants/%s/databases/%s/collections".formatted(tenant, database);

        Map<String, Object> body = Map.of(
                "name", collectionName,
                "get_or_create", true
        );

        Map<String, Object> response = chromaRestClient.post()
                .uri(path)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new RuntimeException("Chroma returned null response while creating/fetching collection");
        }

        if (response.get("id") == null) {
            throw new RuntimeException("Chroma response did not contain a collection id: " + response);
        }

        collectionId = response.get("id").toString();
        return collectionId;
    }

    // Adds text chunks + their embeddings + metadata to the collection
    public void addEmbeddings(
            List<String> ids,
            List<List<Float>> embeddings,
            List<String> documents,
            List<Map<String, Object>> metadatas
    ) {
        String id = getOrCreateCollectionId();

        String path = "/api/v2/tenants/%s/databases/%s/collections/%s/add"
                .formatted(tenant, database, id);

        Map<String, Object> body = Map.of(
                "ids", ids,
                "embeddings", embeddings,
                "documents", documents,
                "metadatas", metadatas
        );

        chromaRestClient.post()
                .uri(path)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    // Queries by embedding similarity, optionally scoped with a metadata "where" filter
    // (e.g. Map.of("username", "john") or Map.of("interviewId", 42)) so results only
    // come from the relevant user's/interview's own resume chunks.
    @SuppressWarnings("unchecked")
    public List<String> queryTopChunks(List<Float> queryEmbedding, int nResults, Map<String, Object> whereFilter) {

        String id = getOrCreateCollectionId();

        String path = "/api/v2/tenants/%s/databases/%s/collections/%s/query"
                .formatted(tenant, database, id);

        Map<String, Object> body = new HashMap<>();
        body.put("query_embeddings", List.of(queryEmbedding));
        body.put("n_results", nResults);
        body.put("include", List.of("documents"));

        if (whereFilter != null && !whereFilter.isEmpty()) {
            body.put("where", buildWhereClause(whereFilter));
        }

        Map<String, Object> response = chromaRestClient.post()
                .uri(path)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new RuntimeException("Chroma returned null response while querying");
        }

        if (response.get("documents") == null) {
            return List.of();
        }

        // "documents" is a list of lists (one inner list per query embedding — we only send one)
        List<List<String>> documents = (List<List<String>>) response.get("documents");

        if (documents.isEmpty()) {
            return List.of();
        }

        return documents.get(0);
    }

    // Chroma's simple equality shorthand {"field": value} only supports a SINGLE condition.
    // For 2+ fields, it requires the explicit $and operator format, or the query fails validation.
    private Object buildWhereClause(Map<String, Object> whereFilter) {

        if (whereFilter.size() == 1) {
            return whereFilter;
        }

        List<Map<String, Object>> conditions = new java.util.ArrayList<>();

        for (Map.Entry<String, Object> entry : whereFilter.entrySet()) {
            conditions.add(Map.of(entry.getKey(), entry.getValue()));
        }

        return Map.of("$and", conditions);
    }
}