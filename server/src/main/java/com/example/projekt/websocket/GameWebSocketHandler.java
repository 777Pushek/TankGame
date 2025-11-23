package com.example.projekt.websocket;

import com.example.projekt.repository.UserRepository;
import com.example.projekt.service.JwtService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final GameSessionManager sessionManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public GameWebSocketHandler(GameSessionManager sessionManager, JwtService jwtService, UserRepository userRepository) {
        this.sessionManager = sessionManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        String path = uri.getPath();
        
        String[] parts = path.split("/");
        if (parts.length < 4) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        Integer lobbyId;
        try {
            lobbyId = Integer.valueOf(parts[3]);
        } catch (NumberFormatException ex) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        
        String query = uri.getQuery();
        String token = null;
        if (StringUtils.hasText(query)) {
            List<String> kvs = List.of(query.split("&"));
            for (String kv : kvs) {
                String[] pair = kv.split("=");
                if (pair.length == 2 && "token".equals(pair[0])) token = pair[1];
            }
        }

        
        if (token != null) {
            try {
                String username = jwtService.extractUsername(token);
                var maybeUser = userRepository.findByUsername(username);
                if (maybeUser.isPresent()) {
                    var user = maybeUser.get();
                    session.getAttributes().put("username", username);
                    session.getAttributes().put("userId", user.getId());
                    session.getAttributes().put("wins", user.getWins());
                    session.getAttributes().put("loses", user.getLoses());
                    session.getAttributes().put("draws", user.getDraws());
                }
            } catch (Exception ignored) {
                
            }
        }

        GameSession gs = sessionManager.getOrCreate(lobbyId);
        gs.add(session);
        session.getAttributes().put("lobbyId", lobbyId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Object lid = session.getAttributes().get("lobbyId");
        if (lid == null) return;
        Integer lobbyId = (Integer) lid;
        GameSession gs = sessionManager.getOrCreate(lobbyId);
        
        gs.onMessage(session, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Object lid = session.getAttributes().get("lobbyId");
        if (lid != null) {
            Integer lobbyId = (Integer) lid;
            GameSession gs = sessionManager.getOrCreate(lobbyId);
            gs.remove(session);
        }
    }
}
