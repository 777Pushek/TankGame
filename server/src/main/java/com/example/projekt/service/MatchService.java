package com.example.projekt.service;

import com.example.projekt.model.Match;
import com.example.projekt.model.MatchPlayer;
import com.example.projekt.model.User;
import com.example.projekt.repository.MatchPlayerRepository;
import com.example.projekt.repository.MatchRepository;
import com.example.projekt.repository.UserRepository;
import com.example.projekt.websocket.PlayerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final UserRepository userRepository;

    public MatchService(MatchRepository matchRepository, MatchPlayerRepository matchPlayerRepository, UserRepository userRepository) {
        this.matchRepository = matchRepository;
        this.matchPlayerRepository = matchPlayerRepository;
        this.userRepository = userRepository;
    }

    public Match saveMatchWithPlayers(LocalDateTime start, long durationMs, Map<Integer, PlayerState> allPlayers, Map<Integer,String> idToUsername, long timeRem, Integer winner) {
        try {
            Match m = new Match();
            m.setStartTime(start);
            m.setDurationMs(durationMs);
            Match saved = matchRepository.save(m);

            List<MatchPlayer> toSave = new ArrayList<>();
            for (Map.Entry<Integer, PlayerState> entry : allPlayers.entrySet()) {
                Integer playerId = entry.getKey();
                PlayerState p = entry.getValue();
                MatchPlayer mp = new MatchPlayer();
                mp.setMatch(saved);
                mp.setUserId(p.id);
                
                String opponentUsername = null;
                for (Integer otherId : allPlayers.keySet()) {
                    if (!otherId.equals(p.id)) {
                        opponentUsername = idToUsername.getOrDefault(otherId, "User " + otherId);
                        break;
                    }
                }
                mp.setOpponentUsername(opponentUsername);
                mp.setTankType(p instanceof PlayerState ? ((PlayerState)p).tankType : "MEDIUM");
                mp.setTimeRemainingMs(timeRem);
                String res = "DRAW";
                if (winner != null) {
                    res = (p.id == winner) ? "WIN" : "LOSE";
                }
                mp.setResult(res);
                toSave.add(mp);
            }
            if (!toSave.isEmpty()) matchPlayerRepository.saveAll(toSave);
            return saved;
        } catch (Exception e) {
            log.error("Failed to persist match and players", e);
            return null;
        }
    }

    public int incrementWins(Integer userId) {
        if (userId == null) return 0;
        try {
            Optional<User> ou = userRepository.findById(userId);
            if (ou.isPresent()) {
                User u = ou.get();
                u.setWins(u.getWins() + 1);
                userRepository.save(u);
                return u.getWins();
            }
        } catch (Exception e) {
            log.error("Failed to increment wins for {}", userId, e);
        }
        return 0;
    }

    public int incrementLoses(Integer userId) {
        if (userId == null) return 0;
        try {
            Optional<User> ou = userRepository.findById(userId);
            if (ou.isPresent()) {
                User u = ou.get();
                u.setLoses(u.getLoses() + 1);
                userRepository.save(u);
                return u.getLoses();
            }
        } catch (Exception e) {
            log.error("Failed to increment loses for {}", userId, e);
        }
        return 0;
    }

    public int incrementDraws(Integer userId) {
        if (userId == null) return 0;
        try {
            Optional<User> ou = userRepository.findById(userId);
            if (ou.isPresent()) {
                User u = ou.get();
                u.setDraws(u.getDraws() + 1);
                userRepository.save(u);
                return u.getDraws();
            }
        } catch (Exception e) {
            log.error("Failed to increment draws for {}", userId, e);
        }
        return 0;
    }
}
