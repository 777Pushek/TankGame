package com.example.projekt.controller;

import com.example.projekt.model.User;
import com.example.projekt.repository.MatchPlayerRepository;
import com.example.projekt.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/leaderboard")
public class LeaderboardController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchPlayerRepository matchPlayerRepository;

        @GetMapping
    public ResponseEntity<?> top10() {
        List<User> top = userRepository.findAllOrderByScore(PageRequest.of(0, 10));
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : top) {
            Map<String, Object> row = new HashMap<>();
            row.put("username", u.getUsername());
            row.put("wins", u.getWins());
            row.put("loses", u.getLoses());
            row.put("draws", u.getDraws());
            String mostUsed = "";
            try {
                List<String> tanks = matchPlayerRepository.findMostUsedTanksByUser(u.getId(), PageRequest.of(0,1));
                if (tanks != null && !tanks.isEmpty()) mostUsed = tanks.get(0);
            } catch (Exception ignored) {}
            row.put("mostUsedTank", mostUsed == null ? "" : mostUsed);
            out.add(row);
        }
        return ResponseEntity.ok(out);
    }
}
