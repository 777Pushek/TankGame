package com.example.projekt.controller;

import com.example.projekt.model.Match;
import com.example.projekt.model.MatchPlayer;
import com.example.projekt.model.User;
import com.example.projekt.repository.MatchPlayerRepository;
import com.example.projekt.repository.MatchRepository;
import com.example.projekt.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/matches")
@Tag(name = "Matches", description = "Endpoints for retrieving match history and match-related data")
public class MatchController {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchPlayerRepository matchPlayerRepository;

    @Autowired
    private UserRepository userRepository;

                @GetMapping("/me")
        @Operation(summary = "Get my matches", description = "Returns paged match history for the authenticated user. Query params: page (0-based), size")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged match history",
                content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "User not found")
        })
        public ResponseEntity<?> myMatches(Authentication authentication,
                           @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
                           @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        String username = authentication.getName();
        Optional<User> maybeUser = userRepository.findByUsername(username);
        if (maybeUser.isEmpty()) return ResponseEntity.status(404).body(Map.of("error","User not found"));
        int uid = maybeUser.get().getId();

        org.springframework.data.domain.Pageable pg = org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, size));
        org.springframework.data.domain.Page<MatchPlayer> pageResult = matchPlayerRepository.findByUserIdOrderByMatchStartDesc(uid, pg);

        List<Object> out = new ArrayList<>();
        for (MatchPlayer mp : pageResult.getContent()) {
            Match m = mp.getMatch();
            java.util.Map<String,Object> row = new java.util.HashMap<>();
            row.put("matchId", m.getId());
            row.put("startTime", m.getStartTime());
            row.put("userId", mp.getUserId());
            row.put("opponentUsername", mp.getOpponentUsername() == null ? "" : mp.getOpponentUsername());
            row.put("tankType", mp.getTankType());
            row.put("timeRemainingMs", mp.getTimeRemainingMs());
            row.put("result", mp.getResult());
            
            try {
                java.util.List<MatchPlayer> others = matchPlayerRepository.findByMatch_Id(m.getId());
                String opponentTank = "";
                for (MatchPlayer other : others) {
                    if (other.getUserId() != mp.getUserId()) {
                        opponentTank = other.getTankType() == null ? "" : other.getTankType();
                        break;
                    }
                }
                row.put("opponentTank", opponentTank);
            } catch (Exception ignored) {
                row.put("opponentTank", "");
            }
            out.add(row);
        }

        java.util.Map<String,Object> resp = new java.util.HashMap<>();
        resp.put("content", out);
        resp.put("page", pageResult.getNumber());
        resp.put("size", pageResult.getSize());
        resp.put("totalElements", pageResult.getTotalElements());
        resp.put("totalPages", pageResult.getTotalPages());

        return ResponseEntity.ok(resp);
    }
}
