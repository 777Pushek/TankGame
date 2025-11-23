package com.example.projekt.websocket;

import com.example.projekt.repository.UserRepository;
import com.example.projekt.repository.MatchRepository;
import com.example.projekt.repository.MatchPlayerRepository;
import com.example.projekt.repository.LobbyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameSessionManager {
    private final Map<Integer, GameSession> map = new ConcurrentHashMap<>();

    @Autowired
    private com.example.projekt.service.MatchService matchService;

    @Autowired
    private LobbyRepository lobbyRepository;

    public GameSession getOrCreate(Integer lobbyId) {
        return map.computeIfAbsent(lobbyId, id -> {
            GameSession gs = new GameSession(id, matchService, lobbyRepository, this);
            return gs;
        });
    }

    public void removeSession(Integer lobbyId) {
        map.remove(lobbyId);
    }

    public boolean isUserConnected(Integer lobbyId, Integer userId) {
        GameSession gs = map.get(lobbyId);
        if (gs == null) return false;
        try {
            return gs.hasPlayer(userId);
        } catch (Exception e) {
            return false;
        }
    }

    public void removeIfEmpty(Integer lobbyId) {
        GameSession gs = map.get(lobbyId);
        
    }
}
