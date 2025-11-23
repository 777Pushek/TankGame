package com.example.tanksclient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class LobbyClient {
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String BASE = "http://localhost:8080";
    private static final Logger LOGGER = Logger.getLogger(LobbyClient.class.getName());

    public static List<Map<String, Object>> listOpen() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/lobbies"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                try {
                    return mapper.readValue(res.body(), new TypeReference<>() {});
                } catch (Exception e) {
                    throw new IOException("Invalid JSON response while listing lobbies: " + e.getMessage() + " - body=" + res.body());
                }
            }
            throw new IOException("Failed to list lobbies: " + res.statusCode() + " - " + res.body());
        } catch (ConnectException | ClosedChannelException ce) {
            LOGGER.log(Level.FINE, "Lobby list fetch failed (connect/closed): " + ce.getMessage());
            return List.of();
        } catch (IOException ioe) {
            throw ioe;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.FINE, "Lobby list fetch interrupted");
            return List.of();
        }
    }

    public static Map<String, Object> createLobby(String token, String name, String password) throws IOException, InterruptedException {
        java.util.Map<String,String> body = new java.util.HashMap<>();
        if (name != null) body.put("name", name);
        if (password != null) body.put("password", password);
        String json = mapper.writeValueAsString(body);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/lobbies"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            try {
                return mapper.readValue(res.body(), new TypeReference<>() {});
            } catch (Exception e) {
                throw new IOException("Invalid JSON response while creating lobby: " + e.getMessage() + " - body=" + res.body());
            }
        }
        throw new IOException("Failed to create lobby: " + res.statusCode() + " - " + res.body());
    }

    public static Map<String, Object> joinLobby(int id, String token, String password) throws IOException, InterruptedException {
        HttpRequest.Builder b;
        if (password == null) {
            b = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/lobbies/" + id + "/join"))
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.noBody());
        } else {
            String json = mapper.writeValueAsString(java.util.Map.of("password", password));
            b = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/lobbies/" + id + "/join"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
        }
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            try {
                return mapper.readValue(res.body(), new TypeReference<>() {});
            } catch (Exception e) {
                throw new IOException("Invalid JSON response while joining lobby: " + e.getMessage() + " - body=" + res.body());
            }
        }
        throw new IOException("Failed to join lobby: " + res.statusCode() + " - " + res.body());
    }

    public static Map<String, Object> readyLobby(int id, String token) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/lobbies/" + id + "/ready"))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            try {
                return mapper.readValue(res.body(), new TypeReference<>() {});
            } catch (Exception e) {
                throw new IOException("Invalid JSON response while setting ready: " + e.getMessage() + " - body=" + res.body());
            }
        }
        throw new IOException("Failed to set ready: " + res.statusCode() + " - " + res.body());
    }
}
