package com.example.tanksclient;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import lombok.Value;

public class AuthClient {
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String BASE = "http://localhost:8080";

    public static LoginResult login(String username, String password) {
        try {
            String json = mapper.writeValueAsString(Map.of("username", username, "password", password));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/auth/login"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            String body = res.body() == null ? "" : res.body();
            if (code == 200) {
                Map<String,Object> map = mapper.readValue(body, Map.class);
                Object token = map.get("token");
                if (token != null) {
                    String tok = token.toString();
                    Map<String,Object> profile = fetchProfile(tok);
                    return new LoginResult(true, "OK", tok, profile);
                } else {
                    return new LoginResult(false, "No token in response", null, null);
                }
            } else if (code == 401) {
                return new LoginResult(false, "Unauthorized", null, null);
            } else {
                return new LoginResult(false, "Login failed: " + code + " - " + body, null, null);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return new LoginResult(false, "Network error: " + e.getMessage(), null, null);
        }
    }

    @Value
    public static class LoginResult {
        boolean success;
        String message;
        String token;
        java.util.Map<String,Object> profile;
    }

    public static Map<String,Object> fetchProfile(String token) {
        if (token == null) return null;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/users/me"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                return mapper.readValue(res.body(), Map.class);
            }
            return null;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
