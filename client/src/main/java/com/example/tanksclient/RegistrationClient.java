package com.example.tanksclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Value;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class RegistrationClient {
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String BASE_URL = "http://localhost:8080";

    public static RegistrationResult register(String username, String password) {
        try {
            Map<String, Object> payload = Map.of(
                    "username", username,
                    "password", password
            );
            String json = mapper.writeValueAsString(payload);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/auth/register"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

            int code = res.statusCode();
            String body = res.body() == null ? "" : res.body();

            if (code == 201) {
                return new RegistrationResult(true, body.isEmpty() ? "Registered" : body, code);
            } else if (code == 409) {
                return new RegistrationResult(false, body.isEmpty() ? "User exists" : body, code);
            } else if (code >= 400 && code < 500) {
                return new RegistrationResult(false, body.isEmpty() ? "Bad request" : body, code);
            } else if (code >= 500) {
                return new RegistrationResult(false, "Server error: " + code, code);
            } else {
                return new RegistrationResult(false, "Unexpected response: " + code + " - " + body, code);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RegistrationResult(false, "Network error: " + e.getMessage(), -1);
        }
    }

    @Value
    public static class RegistrationResult {
        boolean success;
        String message;
        int statusCode;
    }
}
