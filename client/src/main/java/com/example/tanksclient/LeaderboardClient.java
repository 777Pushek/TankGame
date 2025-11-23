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

public class LeaderboardClient {
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String BASE = "http://localhost:8080";
    private static final Logger LOGGER = Logger.getLogger(LeaderboardClient.class.getName());

    public static List<Map<String, Object>> fetchLeaderboard() throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/leaderboard"))
                .timeout(Duration.ofSeconds(10))
                .GET();
        try {
            HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                try {
                    return mapper.readValue(res.body(), new TypeReference<>() {});
                } catch (Exception e) {
                    throw new IOException("Invalid JSON response while fetching leaderboard: " + e.getMessage() + " - body=" + res.body());
                }
            }
            throw new IOException("Failed to fetch leaderboard: " + res.statusCode() + " - " + res.body());
        } catch (ConnectException | ClosedChannelException ce) {
            LOGGER.log(Level.FINE, "Leaderboard fetch failed (connect/closed): " + ce.getMessage());
            return List.of();
        } catch (IOException ioe) {
            throw ioe;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.FINE, "Leaderboard fetch interrupted");
            return List.of();
        }
    }
}
