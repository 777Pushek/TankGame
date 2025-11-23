package com.example.projekt.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.projekt.repository.UserRepository;
import com.example.projekt.repository.MatchRepository;
import com.example.projekt.repository.MatchPlayerRepository;
import com.example.projekt.repository.LobbyRepository;
import com.example.projekt.model.User;
import com.example.projekt.model.Match;
import com.example.projekt.model.MatchPlayer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

public class GameSession {

    private static final Logger log = LoggerFactory.getLogger(GameSession.class);
    private final Integer lobbyId;
    private final Set<WebSocketSession> sessions = Collections.synchronizedSet(new HashSet<>());
    private final ConcurrentMap<Integer, PlayerState> players = new ConcurrentHashMap<>();
    
    private final ConcurrentMap<Integer, PlayerState> recentlyLeft = new ConcurrentHashMap<>();
    private final List<Bullet> bullets = new CopyOnWriteArrayList<>();
    private final List<Target> targets = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();

    private final ScheduledExecutorService ticker;
    private volatile boolean running = false;
    
    private volatile long lastTickTime = 0L;
    
    private volatile boolean movementBarrierActive = true;
    
    private volatile boolean startBarrierActive = false;
    private volatile long barrierEndTime = 0L; 
    
    private volatile long gameStartTime = 0L;
    private volatile long gameDurationMs = 0L;
    
    private volatile long lastTargetSpawn = 0L;
    private volatile long nextTargetDelayMs = 5000L;
    private final java.util.Random rand = new java.util.Random();

    private final com.example.projekt.service.MatchService matchService;
    private final LobbyRepository lobbyRepository;
    private final GameSessionManager manager;

    public GameSession(Integer lobbyId, com.example.projekt.service.MatchService matchService, LobbyRepository lobbyRepository, GameSessionManager manager) {
        this.lobbyId = lobbyId;
        this.matchService = matchService;
        this.lobbyRepository = lobbyRepository;
        this.manager = manager;
        
        this.ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("GameSession-tick-" + lobbyId);
            return t;
        });
    }

    private void recordResult(Integer winner) {
        try {
            if (winner == null) {
                
                for (PlayerState p : players.values()) {
                    int pid = p.id;
                    int newDraws = matchService.incrementDraws(pid);
                    final Object dv = newDraws;
                    synchronized (sessions) {
                        for (WebSocketSession s : sessions) {
                            try {
                                Object uidAttr = s.getAttributes().get("userId");
                                if (uidAttr instanceof Integer && ((Integer) uidAttr).intValue() == pid) {
                                    s.getAttributes().put("draws", dv);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
                
                try { persistMatch(winner); } catch (Exception ignored) {}
                
                try { broadcastState(); } catch (Exception ignored) {}
                return;
            }
            
            int newWins = matchService.incrementWins(winner);
            final Object wv = newWins;
            synchronized (sessions) {
                for (WebSocketSession s : sessions) {
                    try {
                        Object uidAttr = s.getAttributes().get("userId");
                        if (uidAttr instanceof Integer && ((Integer) uidAttr).intValue() == winner) {
                            s.getAttributes().put("wins", wv);
                        }
                    } catch (Exception ignored) {}
                }
            }
            
            for (PlayerState p : players.values()) {
                if (p.id == winner) continue;
                int pid = p.id;
                int newLoses = matchService.incrementLoses(pid);
                final Object lv = newLoses;
                synchronized (sessions) {
                    for (WebSocketSession s : sessions) {
                        try {
                            Object uidAttr = s.getAttributes().get("userId");
                            if (uidAttr instanceof Integer && ((Integer) uidAttr).intValue() == pid) {
                                s.getAttributes().put("loses", lv);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            
            try { persistMatch(winner); } catch (Exception ignored) {}
            
            try { broadcastState(); } catch (Exception ignored) {}
        } catch (Exception e) {
            
        }
    }

        private void persistMatch(Integer winner) {
        try {
            LocalDateTime start = gameStartTime > 0 ?
                    Instant.ofEpochMilli(gameStartTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
                    : LocalDateTime.now();
            long duration = gameDurationMs;
            long timeRem = 0;
            if (gameStartTime > 0 && gameDurationMs > 0) {
                long elapsed = System.currentTimeMillis() - gameStartTime;
                timeRem = Math.max(0, gameDurationMs - elapsed);
            }

            
            java.util.Map<Integer,String> idToUsername = new java.util.HashMap<>();
            synchronized (sessions) {
                for (WebSocketSession s : sessions) {
                    try {
                        Object uid = s.getAttributes().get("userId");
                        if (uid instanceof Integer) {
                            Integer id = (Integer) uid;
                            Object ua = s.getAttributes().get("username");
                            idToUsername.put(id, ua == null ? ("User " + id) : ua.toString());
                        }
                    } catch (Exception ignored) {}
                }
            }

            java.util.Map<Integer, PlayerState> allPlayers = new java.util.HashMap<>(players);
            allPlayers.putAll(recentlyLeft);

            
            matchService.saveMatchWithPlayers(start, duration, allPlayers, idToUsername, timeRem, winner);
            recentlyLeft.clear();
        } catch (Exception e) {
            log.error("Failed to persist match", e);
        }
    }

    

    public void add(WebSocketSession s) {
        sessions.add(s);
        Object uid = s.getAttributes().get("userId");
        if (uid instanceof Integer) {
            players.computeIfAbsent((Integer) uid, PlayerState::new);
        }
    }

    public boolean hasPlayer(Integer userId) {
        if (userId == null) return false;
        return players.containsKey(userId);
    }

        public void startGame() {
        startIfNeeded();
        
        try {
            double worldWidth = 900.0; 
            List<PlayerState> pls = new ArrayList<>(players.values());
            
            
            for (int i = 0; i < pls.size(); i++) {
                PlayerState p = pls.get(i);
                if (i % 2 == 0) {
                    
                    p.x = 200;
                    p.side = -1;
                } else {
                    
                    p.x = worldWidth - 200;
                    p.side = 1;
                }
                p.y = 200;
                p.vx = 0;
                p.vy = 0;
                
                
                if (!p.selected) {
                    p.width = 60; p.height = 30; p.speed = 150; p.maxHealth = 100; p.health = p.maxHealth; p.damage = 12; p.bulletRadius = 5; p.fireRateMs = 400;
                } else {
                    
                    p.health = p.maxHealth;
                }
            }
        } catch (Exception ignored) {
        }

        
    startBarrierActive = true;
    barrierEndTime = System.currentTimeMillis() + 5000;

            
            targets.clear();
            
            lastTargetSpawn = barrierEndTime;
            
            nextTargetDelayMs = 1000L;

        
        this.gameStartTime = System.currentTimeMillis();
        this.gameDurationMs = 3 * 60 * 1000;

        
        try {
            String ctrl = mapper.writeValueAsString(Map.of("type", "control", "event", "started"));
            broadcast(ctrl);
        } catch (Exception ignored) {
        }
        
        broadcastState();
    }

    public void remove(WebSocketSession s) {
        
        sessions.remove(s);
        Object uid = s.getAttributes().get("userId");
        Integer leavingId = uid instanceof Integer ? (Integer) uid : null;
        if (leavingId != null) {
            
            PlayerState leavingPlayer = players.get(leavingId);
            if (leavingPlayer != null && running) {
                recentlyLeft.put(leavingId, leavingPlayer);
            }
            
            players.remove(leavingId);
        }
        
        if (sessions.isEmpty()) {
            stop();
            return;
        }

        
        
        if (running) {
            ticker.execute(() -> {
                try {
                    int aliveCount = 0;
                    Integer lastAlive = null;
                    for (PlayerState p : players.values()) {
                        if (p.health > 0) { aliveCount++; lastAlive = p.id; }
                    }
                    
                    if (aliveCount <= 1) {
                        endGameBecausePlayerLeft(leavingId, aliveCount == 1 ? lastAlive : null);
                    }
                } catch (Exception ignored) {}
            });
        }
    }

        private void endGameBecausePlayerLeft(Integer leaverId, Integer remainingWinnerId) {
        try {
            Integer winToReport = remainingWinnerId;

            
            if (winToReport != null) {
                int newWins = matchService.incrementWins(winToReport);
                final Object wv = newWins;
                synchronized (sessions) {
                    for (WebSocketSession s : sessions) {
                        try {
                            Object uidAttr = s.getAttributes().get("userId");
                            if (uidAttr instanceof Integer && ((Integer) uidAttr).intValue() == winToReport) {
                                s.getAttributes().put("wins", wv);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            
            java.util.Set<Integer> toMarkLoses = new java.util.HashSet<>();
            for (PlayerState p : players.values()) {
                if (winToReport == null || p.id != winToReport) toMarkLoses.add(p.id);
            }
            if (leaverId != null && (winToReport == null || !leaverId.equals(winToReport))) toMarkLoses.add(leaverId);

            for (Integer pid : toMarkLoses) {
                int newLoses = matchService.incrementLoses(pid);
                final Object lv = newLoses;
                synchronized (sessions) {
                    for (WebSocketSession s : sessions) {
                        try {
                            Object uidAttr = s.getAttributes().get("userId");
                            if (uidAttr instanceof Integer && ((Integer) uidAttr).intValue() == pid) {
                                s.getAttributes().put("loses", lv);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            
            
            try { persistMatch(winToReport); } catch (Exception ignored) {}
            try {
                java.util.Map<String, Object> ev = new java.util.HashMap<>();
                ev.put("type", "control");
                ev.put("event", "ended");
                ev.put("winner", winToReport);
                String json = mapper.writeValueAsString(ev);
                broadcast(json);
            } catch (Exception ignored) {}

            
            try { broadcastState(); } catch (Exception ignored) {}
        } catch (Exception ignored) {
        } finally {
            stop();
        }
    }

    private void startIfNeeded() {
        if (!running) {
            running = true;
            
            lastTickTime = System.currentTimeMillis();
            ticker.scheduleAtFixedRate(this::tick, 0, 16, TimeUnit.MILLISECONDS);
        }
    }

    private void stop() {
        running = false;
        ticker.shutdownNow();
        try {
            
            if (lobbyRepository != null) {
                try { lobbyRepository.deleteById(lobbyId); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        try {
            if (manager != null) manager.removeSession(lobbyId);
        } catch (Exception ignored) {}
    }

        public void onMessage(WebSocketSession session, String payload) {
        try {
            Map<String, Object> m = mapper.readValue(payload, Map.class);
            String type = m.getOrDefault("type", "").toString();
            if ("input".equals(type)) {
                Object uid = session.getAttributes().get("userId");
                Integer userId = uid instanceof Integer ? (Integer) uid : session.hashCode();
                PlayerState ps = players.computeIfAbsent(userId, PlayerState::new);
                String action = m.getOrDefault("action", "").toString();
                if ("move".equals(action)) {
                    Number dx = (Number) m.getOrDefault("dx", 0);
                    Number dy = (Number) m.getOrDefault("dy", 0);
                    
                    double ddx = dx.doubleValue();
                    double ddy = dy.doubleValue();
                    ps.vx = ddx * ps.speed;
                    ps.vy = ddy * ps.speed;
                } else if ("shoot".equals(action)) {
        
        if (startBarrierActive) {
            if (System.currentTimeMillis() >= barrierEndTime) {
                startBarrierActive = false;
            }
        }
                        double bx = ps.x;
                        double by = ps.y;
                    
                    Number nTx = (Number) m.getOrDefault("tx", 0);
                    Number nTy = (Number) m.getOrDefault("ty", 0);
                    double tx = nTx.doubleValue();
                    double ty = nTy.doubleValue();
                    
                    try {
                        ps.angle = Math.atan2(ty - ps.y, tx - ps.x);
                    } catch (Exception ignored) {}
                    double dirx = tx - bx;
                    double diry = ty - by;
                    double len = Math.max(1e-6, Math.hypot(dirx, diry));
                    double speed = 400.0; 
                    double vx = dirx / len * speed;
                    double vy = diry / len * speed;
                    
                    long now = System.currentTimeMillis();
                    if (now - ps.lastShotTime >= ps.fireRateMs) {
                        ps.lastShotTime = now;
                        Bullet b = new Bullet(userId, bx, by, vx, vy, ps.damage, ps.bulletRadius);
                        bullets.add(b);
                    } else {
                        
                    }
                } else if ("setpos".equals(action)) {
                    Number x = (Number) m.getOrDefault("x", 0);
                    Number y = (Number) m.getOrDefault("y", 0);
                    ps.x = x.doubleValue();
                    ps.y = y.doubleValue();
                } else if ("aim".equals(action)) {
                    
                    Number nTx = (Number) m.getOrDefault("tx", 0);
                    Number nTy = (Number) m.getOrDefault("ty", 0);
                    double tx = nTx.doubleValue();
                    double ty = nTy.doubleValue();
                    try { ps.angle = Math.atan2(ty - ps.y, tx - ps.x); } catch (Exception ignored) {}
                } else if ("select".equals(action)) {
                    
                    Object tt = m.getOrDefault("tankType", "MEDIUM");
                    String tankType = tt == null ? "MEDIUM" : tt.toString().toUpperCase();
                    switch (tankType) {
                        case "SMALL" -> {
                            ps.width = 40; ps.height = 20; ps.speed = 220; ps.maxHealth = 70; ps.health = ps.maxHealth; ps.damage = 8; ps.bulletRadius = 3;
                                    ps.fireRateMs = 200;
                        }
                        case "LARGE" -> {
                            ps.width = 80; ps.height = 40; ps.speed = 100; ps.maxHealth = 150; ps.health = ps.maxHealth; ps.damage = 20; ps.bulletRadius = 8;
                                    ps.fireRateMs = 700;
                        }
                        default -> {
                            ps.width = 60; ps.height = 30; ps.speed = 150; ps.maxHealth = 100; ps.health = ps.maxHealth; ps.damage = 12; ps.bulletRadius = 5;
                                    ps.fireRateMs = 400;
                        }
                    }
                    
                    ps.selected = true;
                    ps.tankType = tankType;
                    
                    
                    if (!running) {
                        boolean allSelected = players.size() >= 2;
                        if (allSelected) {
                            for (PlayerState p : players.values()) {
                                if (!p.selected) { allSelected = false; break; }
                            }
                        }
                        if (allSelected) {
                            
                            startIfNeeded();
                            ticker.execute(this::startGame);
                        }
                    }
                } else if ("buy".equals(action)) {
                    
                    String item = String.valueOf(m.getOrDefault("item", ""));
                    int cost = switch (item) {
                        case "speed" -> 50;
                        case "damage" -> 50;
                        case "hp" -> 50;
                        case "bullet" -> 50;
                        default -> 0;
                    };
                    if (cost > 0 && ps.money >= cost) {
                        ps.money -= cost;
                        switch (item) {
                            case "speed" -> ps.speed += 30;
                            case "damage" -> ps.damage += 5;
                            case "hp" -> { ps.maxHealth += 20; ps.health = Math.min(ps.maxHealth, ps.health + 20); }
                            case "bullet" -> ps.bulletRadius += 1.0;
                        }
                            
                            try { ps.bonuses.put(item, ps.bonuses.getOrDefault(item, 0) + 1); } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            
        }
    }

    private void tick() {
        
        long tickNow = System.currentTimeMillis();
        double dt = 0.05; 
        if (lastTickTime > 0) {
            dt = Math.max(0.001, (tickNow - lastTickTime) / 1000.0);
        }
        lastTickTime = tickNow;
        
        updatePlayers(dt);
        updateBullets(dt);
        long now = System.currentTimeMillis();
        spawnTargets(now);
        processTargetCollisions(dt);
        checkEndConditions();
        broadcastState();
    }

    private void updatePlayers(double dt) {
        double worldWidth = 900.0;
        double worldHeight = 600.0;
        double barrierW = 20;
        double barrierCenterX = worldWidth / 2.0;
        double leftLimitBase = barrierCenterX - (barrierW/2.0);
        double rightLimitBase = barrierCenterX + (barrierW/2.0);
        for (PlayerState p : players.values()) {
            double nextX = p.x + p.vx * dt;
            double nextY = p.y + p.vy * dt;
            if (movementBarrierActive && p.side != 0) {
                double leftLimit = leftLimitBase - (p.width/2.0) - 1.0;
                double rightLimit = rightLimitBase + (p.width/2.0) + 1.0;
                if (p.side < 0) {
                    if (nextX > leftLimit) { nextX = leftLimit; p.vx = 0; }
                } else if (p.side > 0) {
                    if (nextX < rightLimit) { nextX = rightLimit; p.vx = 0; }
                }
            }
            p.x = nextX; p.y = nextY;
            p.vx *= 0.9; p.vy *= 0.9;
            double minX = p.width/2.0; double maxX = worldWidth - p.width/2.0;
            double minY = p.height/2.0; double maxY = worldHeight - p.height/2.0;
            if (p.x < minX) { p.x = minX; p.vx = 0; } else if (p.x > maxX) { p.x = maxX; p.vx = 0; }
            if (p.y < minY) { p.y = minY; p.vy = 0; } else if (p.y > maxY) { p.y = maxY; p.vy = 0; }
        }
    }

    private void updateBullets(double dt) {
        double worldWidth = 900.0; double worldHeight = 600.0;
        double barrierW = 20; double barrierCenterX = worldWidth / 2.0;
        List<Bullet> toRemove = new ArrayList<>();
        for (Bullet b : bullets) {
            b.x += b.vx * dt; b.y += b.vy * dt;
            if (startBarrierActive) {
                double barrierX = barrierCenterX - barrierW/2.0; double barrierY = 0; double barrierH = worldHeight;
                if (b.x >= barrierX && b.x <= barrierX + barrierW && b.y >= barrierY && b.y <= barrierY + barrierH) { toRemove.add(b); continue; }
            }
            for (PlayerState p : players.values()) {
                if (p.id == b.ownerId) continue;
                double px = p.x; double py = p.y; double dist = Math.hypot(b.x - px, b.y - py);
                if (dist <= b.radius + Math.max(p.width,p.height)/2.0) { p.health -= b.damage; toRemove.add(b); break; }
            }
            if (b.x < -50 || b.x > worldWidth + 50 || b.y < -50 || b.y > worldHeight + 50) toRemove.add(b);
        }
        if (!toRemove.isEmpty()) bullets.removeAll(toRemove);
    }

    private void spawnTargets(long now) {
        if (now - lastTargetSpawn >= nextTargetDelayMs) {
            if (startBarrierActive && now < barrierEndTime) {
                lastTargetSpawn = now;
            } else {
                lastTargetSpawn = now;
                nextTargetDelayMs = 5000 + rand.nextInt(10000);
                double w = 900.0;
                double x = 40 + rand.nextDouble() * (w - 80);
                Target t = new Target(x, -20, 0, 80, 20, 12);
                targets.add(t);
            }
        }
    }

    private void processTargetCollisions(double dt) {
        double worldHeight = 600.0;
        List<Target> targetsToRemove = new ArrayList<>();
        List<Bullet> bulletsToRemoveFromTargets = new ArrayList<>();
        for (Target t : targets) {
            t.y += t.vy * dt;
            for (Bullet b : bullets) {
                double dx = b.x - t.x; double dy = b.y - t.y;
                if (dx*dx + dy*dy <= (b.radius + t.radius)*(b.radius + t.radius)) {
                    t.health -= b.damage;
                    bulletsToRemoveFromTargets.add(b);
                    log.debug("Bullet hit target: owner={} bx={} by={} targetX={} targetY={} remainingHealth={}", b.ownerId, b.x, b.y, t.x, t.y, t.health);
                    if (t.health <= 0) {
                        players.computeIfPresent(b.ownerId, (k, ps) -> { ps.money += 50; return ps; });
                        log.debug("Target destroyed by owner={} awarding +50", b.ownerId);
                    }
                    break;
                }
            }
            if (t.health <= 0) targetsToRemove.add(t);
            if (t.y > worldHeight + 50) targetsToRemove.add(t);
        }
        if (!bulletsToRemoveFromTargets.isEmpty()) bullets.removeAll(bulletsToRemoveFromTargets);
        if (!targetsToRemove.isEmpty()) targets.removeAll(targetsToRemove);
    }

    private void checkEndConditions() {
        if (gameStartTime > 0 && gameDurationMs > 0) {
            long elapsed = System.currentTimeMillis() - gameStartTime;
            if (elapsed >= gameDurationMs && running) {
                Integer winner = null; int alive = 0;
                for (PlayerState p : players.values()) if (p.health > 0) { winner = p.id; alive++; }
                Integer winToReport = alive == 1 ? winner : null;
                recordResult(winToReport);
                try {
                    java.util.Map<String,Object> ev = new java.util.HashMap<>();
                    ev.put("type","control"); ev.put("event","ended"); ev.put("winner", winToReport);
                    String json = mapper.writeValueAsString(ev); broadcast(json);
                } catch (Exception ignored) {}
                stop();
                return;
            }
        }
        int aliveCount = 0; Integer winner = null;
        for (PlayerState p : players.values()) { if (p.health > 0) { aliveCount++; winner = p.id; } }
        if (players.size() > 0 && aliveCount <= 1 && running) {
            try {
                recordResult(winner);
                java.util.Map<String,Object> ev = new java.util.HashMap<>(); ev.put("type","control"); ev.put("event","ended"); ev.put("winner", winner);
                String json = mapper.writeValueAsString(ev); broadcast(json);
            } catch (Exception ignored) {}
            stop();
        }
    }

    private void broadcastState() {
        List<Map<String, Object>> pls = new ArrayList<>();
    for (PlayerState p : players.values()) {
        
        Object usernameAttr = null; Object winsAttr = null; Object losesAttr = null; Object drawsAttr = null;
        synchronized (sessions) {
            for (WebSocketSession s : sessions) {
                try {
                    Object uid = s.getAttributes().get("userId");
                    if (uid instanceof Integer && ((Integer) uid).intValue() == p.id) {
                        usernameAttr = s.getAttributes().get("username");
                        winsAttr = s.getAttributes().get("wins");
                        losesAttr = s.getAttributes().get("loses");
                        drawsAttr = s.getAttributes().get("draws");
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }
        String uname = usernameAttr == null ? ("User " + p.id) : String.valueOf(usernameAttr);
        int uwins = winsAttr instanceof Number ? ((Number)winsAttr).intValue() : 0;
        int ulose = losesAttr instanceof Number ? ((Number)losesAttr).intValue() : 0;
        int udraws = drawsAttr instanceof Number ? ((Number)drawsAttr).intValue() : 0;
        java.util.Map<String,Object> playerMap = new java.util.HashMap<>();
        playerMap.put("id", p.id);
        playerMap.put("x", p.x);
        playerMap.put("y", p.y);
        playerMap.put("health", p.health);
        playerMap.put("maxHealth", p.maxHealth);
        playerMap.put("width", p.width);
        playerMap.put("height", p.height);
        playerMap.put("money", p.money);
        playerMap.put("angle", p.angle);
        playerMap.put("username", uname);
        playerMap.put("wins", uwins);
        playerMap.put("loses", ulose);
        playerMap.put("draws", udraws);
        
        try { playerMap.put("bonuses", p.bonuses == null ? java.util.Map.of() : new java.util.HashMap<>(p.bonuses)); } catch (Exception ignored) { playerMap.put("bonuses", java.util.Map.of()); }
        pls.add(playerMap);
    }
        List<Map<String,Object>> bls = new ArrayList<>();
        for (Bullet b : bullets) {
            bls.add(Map.of(
                    "owner", b.ownerId,
                    "x", b.x,
                    "y", b.y,
                    "r", b.radius
            ));
        }
        List<Map<String,Object>> tls = new ArrayList<>();
        for (Target t : targets) {
            tls.add(Map.of(
                "x", t.x,
                "y", t.y,
                "r", t.radius,
                "health", t.health,
                "maxHealth", t.maxHealth
            ));
        }
        long timeRem = 0;
        if (gameStartTime > 0 && gameDurationMs > 0) {
            long elapsed = System.currentTimeMillis() - gameStartTime;
            timeRem = Math.max(0, gameDurationMs - elapsed);
        }
        long startBarrierRem = 0;
        if (startBarrierActive) {
            startBarrierRem = Math.max(0, barrierEndTime - System.currentTimeMillis());
        }
    Map<String, Object> out = Map.of(
        "type", "state",
        "players", pls,
        "bullets", bls,
        "targets", tls,
        
        "barrierMovementActive", movementBarrierActive,
        
        "barrierStartActive", startBarrierActive,
        "barrierStartRemainingMs", startBarrierRem,
        "timeRemainingMs", timeRem
    );
        try {
            String json = mapper.writeValueAsString(out);
            broadcast(json);
        } catch (Exception e) {
            
        }
    }

    public void broadcast(String payload) {
        TextMessage msg = new TextMessage(payload);
        synchronized (sessions) {
            for (WebSocketSession s : sessions) {
                try {
                    if (s.isOpen()) s.sendMessage(msg);
                } catch (IOException e) {
                    
                }
            }
        }
    }

    
}
