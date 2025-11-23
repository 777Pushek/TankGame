package com.example.tanksclient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class MatchClient {
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String BASE = "http://localhost:8080";

    public static List<Map<String, Object>> fetchMyMatches(String token) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/matches/me"))
                .timeout(Duration.ofSeconds(10))
                .GET();
        if (token != null && !token.isBlank()) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            try {
                return mapper.readValue(res.body(), new TypeReference<>() {});
            } catch (Exception e) {
                throw new IOException("Invalid JSON response while fetching matches: " + e.getMessage() + " - body=" + res.body());
            }
        }
        throw new IOException("Failed to fetch matches: " + res.statusCode() + " - " + res.body());
    }

    public static Map<String,Object> fetchMyMatchesPage(String token, int page, int size) throws IOException, InterruptedException {
        String uri = String.format(BASE + "/matches/me?page=%d&size=%d", page, size);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(Duration.ofSeconds(10))
                .GET();
        if (token != null && !token.isBlank()) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            try {
                return mapper.readValue(res.body(), new TypeReference<>() {});
            } catch (Exception e) {
                throw new IOException("Invalid JSON response while fetching matches page: " + e.getMessage() + " - body=" + res.body());
            }
        }
        throw new IOException("Failed to fetch matches page: " + res.statusCode() + " - " + res.body());
    }
}
