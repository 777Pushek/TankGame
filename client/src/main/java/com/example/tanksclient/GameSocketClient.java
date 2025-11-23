package com.example.tanksclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

public class GameSocketClient implements WebSocket.Listener {
    private WebSocket ws;
    private final MessageHandler handler;
    private final StringBuilder textBuffer = new StringBuilder();

    public interface MessageHandler {
        void onMessage(String msg);
    }

    public GameSocketClient(MessageHandler handler) {
        this.handler = handler;
    }

    public void connect(int lobbyId, String token) {
        try {
            String uri = String.format("ws://localhost:8080/ws/game/%d", lobbyId);
            if (token != null && !token.isBlank()) {
                try {
                    uri = uri + "?token=" + java.net.URLEncoder.encode(token, "UTF-8");
                } catch (Exception e) {
                    uri = uri + "?token=" + token;
                }
            }
            HttpClient client = HttpClient.newHttpClient();
            var builder = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10));
            if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
            CountDownLatch latch = new CountDownLatch(1);
            builder.buildAsync(URI.create(uri), this).thenAccept(s -> {
                this.ws = s;
                latch.countDown();
            });
            latch.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void sendText(String msg) {
        if (ws != null) ws.sendText(msg, true);
    }

    public void close() {
        if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            textBuffer.append(data);
            if (last) {
                String msg = textBuffer.toString();
                textBuffer.setLength(0);
                Platform.runLater(() -> handler.onMessage(msg));
            }
        } catch (Exception e) {
            Platform.runLater(() -> handler.onMessage("__ERROR__:" + e.getMessage()));
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        Platform.runLater(() -> handler.onMessage("__ERROR__:" + error.getMessage()));
    }
}
