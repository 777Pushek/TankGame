package com.example.projekt.controller;

import com.example.projekt.model.Lobby;
import com.example.projekt.model.User;
import com.example.projekt.repository.LobbyRepository;
import com.example.projekt.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.projekt.websocket.GameSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;

import java.util.*;

@RestController
@RequestMapping("/lobbies")
@Tag(name = "Lobbies", description = "Endpoints for creating, joining and managing game lobbies")
public class LobbyController {

    private static final Logger log = LoggerFactory.getLogger(LobbyController.class);

    @Autowired
    private LobbyRepository lobbyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GameSessionManager sessionManager;

        
        @GetMapping
        @Operation(summary = "List open lobbies", description = "Returns list of currently open lobbies")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of lobbies",
                content = @Content(mediaType = "application/json"))
        })
        public List<Map<String, Object>> listOpen() {
        List<Lobby> lobbies = lobbyRepository.findAllByHostIdIsNotNull();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Lobby l : lobbies) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", l.getId());
            m.put("name", l.getName());
            m.put("hostId", l.getHostId());
            
            
            String hostUsername = "";
            if (l.getHostId() != null) {
                Optional<User> hostUser = userRepository.findById(l.getHostId());
                if (hostUser.isPresent()) {
                    hostUsername = hostUser.get().getUsername();
                }
            }
            m.put("hostUsername", hostUsername);
            
            m.put("guestId", l.getGuestId());
            m.put("hasPassword", l.getPassword() != null && !l.getPassword().isEmpty());
            out.add(m);
        }
        return out;
    }

        
        @PostMapping
        @Operation(summary = "Create lobby", description = "Create a new lobby; optional name and password")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lobby created or returned",
                content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
        })
        public ResponseEntity<?> createLobby(@RequestBody Map<String, String> body, Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).body("Unauthorized");
        String username = authentication.getName();
        Optional<User> maybe = userRepository.findByUsername(username);
        if (maybe.isEmpty()) return ResponseEntity.status(404).body("User not found");
        Integer userId = maybe.get().getId();

        String name = body.getOrDefault("name", null);
        String password = body.getOrDefault("password", null);

        if (name != null && !name.isEmpty()) {
            if (lobbyRepository.existsByName(name)) {
                return ResponseEntity.status(400).body("Lobby name already exists");
            }
        }

        Lobby l = new Lobby();
        l.setHostId(userId);
        l.setGuestId(null);
        l.setName(name);
        l.setPassword(password);
        Lobby saved = lobbyRepository.save(l);
        return ResponseEntity.ok(saved);
    }

        
        @PostMapping("/{id}/join")
        @Operation(summary = "Join lobby", description = "Join a lobby by id; provide password if required")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Joined lobby", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Invalid lobby password"),
            @ApiResponse(responseCode = "404", description = "Lobby or user not found")
        })
        public ResponseEntity<?> joinLobby(@PathVariable Integer id, @RequestBody(required = false) Map<String, String> body, Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).body("Unauthorized");
        String username = authentication.getName();
        log.info("joinLobby called by user='{}' for lobbyId={}", username, id);
        Optional<User> maybe = userRepository.findByUsername(username);
        if (maybe.isEmpty()) return ResponseEntity.status(404).body("User not found");
        Integer userId = maybe.get().getId();

        Optional<Lobby> ol = lobbyRepository.findById(id);
        if (ol.isEmpty()) return ResponseEntity.status(404).body("Lobby not found");
        Lobby l = ol.get();
        
        Integer hid = l.getHostId();
        if (hid != null && !sessionManager.isUserConnected(id, hid)) {
            l.setHostId(null);
            
            lobbyRepository.save(l);
            
            l = lobbyRepository.findById(id).orElse(l);
        }
        
        if (l.getGuestId() != null && l.getHostId() == null) {
            if (l.getGuestId().equals(userId)) {
                
                Map<String, Object> m = new HashMap<>();
                m.put("id", l.getId());
                m.put("name", l.getName());
                m.put("hostId", l.getHostId());
                m.put("guestId", l.getGuestId());
                return ResponseEntity.ok(m);
            }
            
            l.setHostId(userId);
            l.setHostReady(false);
            l.setGuestReady(false);
            lobbyRepository.save(l);
            Map<String, Object> m = new HashMap<>();
            m.put("id", l.getId());
            m.put("name", l.getName());
            m.put("hostId", l.getHostId());
            m.put("guestId", l.getGuestId());
            return ResponseEntity.ok(m);
        }

        if (l.getGuestId() != null) {
            return ResponseEntity.status(400).body("Lobby is full");
        }

        if (l.getHostId() != null && l.getHostId().equals(userId)) {
            return ResponseEntity.status(400).body("Host is already in the lobby");
        }
        
        String provided = body == null ? null : body.get("password");
        if (l.getPassword() != null && !l.getPassword().isEmpty()) {
            if (provided == null || !provided.equals(l.getPassword())) {
                return ResponseEntity.status(403).body("Invalid lobby password");
            }
        }

        l.setGuestId(userId);
        l.setHostReady(false);
        l.setGuestReady(false);
        lobbyRepository.save(l);
        log.info("User {} joined lobby {} as guestId={}", userId, id, l.getGuestId());
        
        Map<String, Object> m = new HashMap<>();
        m.put("id", l.getId());
        m.put("name", l.getName());
        m.put("hostId", l.getHostId());
        m.put("guestId", l.getGuestId());
        return ResponseEntity.ok(m);
    }

        
        @PostMapping("/{id}/leave")
        @Operation(summary = "Leave lobby", description = "Leave a lobby the authenticated user is part of")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Left lobby or deleted if empty", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "400", description = "User not part of lobby"),
            @ApiResponse(responseCode = "404", description = "Lobby or user not found")
        })
        public ResponseEntity<?> leaveLobby(@PathVariable Integer id, Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).body("Unauthorized");
        String username = authentication.getName();
        Optional<User> maybe = userRepository.findByUsername(username);
        if (maybe.isEmpty()) return ResponseEntity.status(404).body("User not found");
        Integer userId = maybe.get().getId();

        Optional<Lobby> ol = lobbyRepository.findById(id);
        if (ol.isEmpty()) return ResponseEntity.status(404).body("Lobby not found");
        Lobby l = ol.get();

        boolean changed = false;
        if (l.getHostId() != null && l.getHostId().equals(userId)) {
            l.setHostId(null);
            changed = true;
        }
        if (l.getGuestId() != null && l.getGuestId().equals(userId)) {
            l.setGuestId(null);
            changed = true;
        }

        if (!changed) return ResponseEntity.status(400).body("User not part of lobby");

        
        if (l.getHostId() == null && l.getGuestId() == null) {
            lobbyRepository.deleteById(l.getId());
            return ResponseEntity.ok(Map.of("deleted", true));
        }

        
        l.setHostReady(false);
        l.setGuestReady(false);
        lobbyRepository.save(l);
        return ResponseEntity.ok(l);
    }

        
        @PostMapping("/{id}/ready")
        @Operation(summary = "Set ready", description = "Mark the authenticated user as ready in the lobby; when both ready, lobby starts")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ready state updated or lobby started", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Lobby or user not found"),
            @ApiResponse(responseCode = "400", description = "User not part of lobby")
        })
        public ResponseEntity<?> readyLobby(@PathVariable Integer id, Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).body("Unauthorized");
        String username = authentication.getName();
        log.info("readyLobby called by user='{}' for lobbyId={}", username, id);
        Optional<User> maybe = userRepository.findByUsername(username);
        if (maybe.isEmpty()) return ResponseEntity.status(404).body("User not found");
        Integer userId = maybe.get().getId();

        Optional<Lobby> ol = lobbyRepository.findById(id);
        if (ol.isEmpty()) return ResponseEntity.status(404).body("Lobby not found");
        Lobby l = ol.get();

        if (l.getHostId() != null && l.getHostId().equals(userId)) {
            l.setHostReady(true);
        } else if (l.getGuestId() != null && l.getGuestId().equals(userId)) {
            l.setGuestReady(true);
        } else {
            return ResponseEntity.status(400).body("User not part of lobby");
        }

        lobbyRepository.save(l);

        if (l.getHostId() != null && l.getGuestId() != null && l.isHostReady() && l.isGuestReady()) {
            
            log.info("Lobby {} starting; deleting lobby record", id);
            lobbyRepository.deleteById(id);
            
            return ResponseEntity.ok(Map.of("started", true));
        }

        return ResponseEntity.ok(l);
    }
}
