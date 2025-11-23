package com.example.tanksclient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.layout.AnchorPane;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameController {
    @FXML
    private Canvas gameCanvas;

    @FXML
    private ProgressBar playerHealthBar;

    @FXML
    private ProgressBar enemyHealthBar;

    @FXML
    private Label playerHealthLabel;

    @FXML
    private Label enemyHealthLabel;

    @FXML
    private Label usernameLabel;
    @FXML
    private Label enemyUsernameLabel;

    @FXML
    private Label timerLabel;

    @FXML
    private javafx.scene.layout.VBox selectionPane;

    @FXML
    private Label moneyLabel;
    @FXML
    private Label enemyMoneyLabel;

    @FXML
    private Label bonusesLabel;
    @FXML
    private Label enemyBonusesLabel;

    @FXML
    private Label playerStatsLabel;

    @FXML
    private Label enemyStatsLabel;

    @FXML
    private Label startCountdownLabel;

    @FXML
    private Button shopButton;

    @FXML
    private AnchorPane shopPane;

    @FXML
    private javafx.scene.layout.VBox endPane;

    @FXML
    private Label endMessageLabel;


    @FXML
    private Label statusLabel;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(GameController.class.getName());
    private final GameRenderer renderer = new GameRenderer();
    private GameSocketClient socketClient;

    private int lobbyId;
    private String token;
    private volatile boolean gameStarted = false;
    private long lastAimSent = 0L;
    private final ConcurrentMap<KeyCode, Boolean> keys = new ConcurrentHashMap<>();
    private AnimationTimer inputTimer;
    private AnimationTimer renderLoop;

    private final ConcurrentMap<Integer, Tank> tanks = new ConcurrentHashMap<>();
    private final List<SimpleBullet> bullets = new CopyOnWriteArrayList<>();
    private final List<SimpleTarget> targets = new CopyOnWriteArrayList<>();
    private long serverTimeRemainingMs = 0;
    private boolean serverMovementBarrierActive = false;
    private boolean serverStartBarrierActive = false;
    private long serverStartBarrierRemainingMs = 0;
    private int myUserId = -1;
    private int money = 0;
    private int enemyMoney = 0;
    private String myUsername = "You";
    private final ConcurrentMap<Integer, Integer> moneyById = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Integer> winsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Integer> losesById = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Integer> drawsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, String> usernameById = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Integer> bonuses = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Map<String,Integer>> bonusesById = new ConcurrentHashMap<>();

    public void init(int lobbyId, String token) {
        this.lobbyId = lobbyId;
        this.token = token;
        if (statusLabel != null) statusLabel.setVisible(false);
        socketClient = new GameSocketClient(this::onMessage);
        socketClient.connect(lobbyId, token);

        new Thread(() -> {
            try {
                var profile = AuthClient.fetchProfile(token);
                if (profile != null) {
                    Object id = profile.get("id");
                    if (id instanceof Number) myUserId = ((Number) id).intValue();
                    Object uname = profile.getOrDefault("username", profile.getOrDefault("name", "You"));
                    if (uname != null) myUsername = uname.toString();
                }
            } catch (Exception ex) { LOGGER.log(Level.FINE, "Background profile fetch failed", ex); }
        }).start();

        Platform.runLater(() -> {
            var scene = gameCanvas.getScene();
            if (scene != null) {
                scene.addEventHandler(KeyEvent.KEY_PRESSED, this::onKeyPressed);
                scene.addEventHandler(KeyEvent.KEY_RELEASED, this::onKeyReleased);
                gameCanvas.requestFocus();
                gameCanvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
                gameCanvas.addEventHandler(MouseEvent.MOUSE_MOVED, this::onMouseMoved);
                gameCanvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
            }

            inputTimer = new AnimationTimer() {
                private long last = 0;
                @Override
                public void handle(long now) {
                    if (last == 0) last = now;
                    if (!gameStarted) return;
                    long elapsed = now - last;
                    if (elapsed >= 16_000_000L) {
                        sendInputFromKeys();
                        last = now;
                    }
                }
            };
            inputTimer.start();

            renderLoop = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    render();
                    updateUI();
                }
            };
            renderLoop.start();
        });
    }

    

    private void onMessage(String msg) {
        try {
            if (msg.startsWith("__ERROR__:")) {
                LOGGER.warning(msg);
                return;
            }
            String raw = msg == null ? "" : msg.trim();
            if (!raw.startsWith("{") && !raw.startsWith("[")) {
                return;
            }
            Map<String,Object> m = mapper.readValue(msg, new TypeReference<>() {});
            String type = (String) m.getOrDefault("type", "");
            if ("control".equals(type)) {
                String ev = (String) m.getOrDefault("event", "");
                if ("started".equals(ev)) {
                    gameStarted = true;
                } else if ("ended".equals(ev)) {
                    gameStarted = false;
                    if (inputTimer != null) inputTimer.stop();
                    Object winner = m.get("winner");
                    String resultText;
                    if (winner == null) {
                        resultText = "DRAW";
                    } else {
                        int winId = -1;
                        try {
                            if (winner instanceof Number) winId = ((Number) winner).intValue();
                            else winId = Integer.parseInt(winner.toString());
                        } catch (Exception ex) { LOGGER.log(Level.FINE, "Parse bonus entry failed", ex); }
                        if (myUserId >= 0 && winId == myUserId) resultText = "YOU WIN";
                        else if (myUserId >= 0) resultText = "YOU LOSE";
                        else resultText = "Winner: " + winner.toString();
                    }
                    final String uiText = resultText;
                    Platform.runLater(() -> {
                        if (endMessageLabel != null) {
                            endMessageLabel.setText(uiText);
                            if ("YOU WIN".equals(uiText)) {
                                endMessageLabel.setStyle("-fx-text-fill: lime; -fx-font-size:48px; -fx-font-weight:bold;");
                            } else if ("YOU LOSE".equals(uiText)) {
                                endMessageLabel.setStyle("-fx-text-fill: red; -fx-font-size:48px; -fx-font-weight:bold;");
                            } else {
                                endMessageLabel.setStyle("-fx-text-fill: white; -fx-font-size:36px; -fx-font-weight:bold;");
                            }
                        }
                        if (endPane != null) endPane.setVisible(true);
                    });
                }
                return;
            } else if ("state".equals(type)) {
                Object playersObj = m.get("players");
                Object bulletsObj = m.get("bullets");
                Object movementBarrierObj = m.get("barrierMovementActive");
                Object startBarrierObj = m.get("barrierStartActive");
                Object startBarrierRemObj = m.get("barrierStartRemainingMs");
                Object timeRemObj = m.get("timeRemainingMs");

                @SuppressWarnings("unchecked")
                List<Map<String,Object>> pls = playersObj instanceof List ? (List<Map<String,Object>>) playersObj : List.of();
                @SuppressWarnings("unchecked")
                List<Map<String,Object>> bls = bulletsObj instanceof List ? (List<Map<String,Object>>) bulletsObj : List.of();

                serverMovementBarrierActive = Boolean.TRUE.equals(movementBarrierObj) || "true".equals(String.valueOf(movementBarrierObj));
                serverStartBarrierActive = Boolean.TRUE.equals(startBarrierObj) || "true".equals(String.valueOf(startBarrierObj));
                serverStartBarrierRemainingMs = startBarrierRemObj instanceof Number ? ((Number)startBarrierRemObj).longValue() : 0L;
                
                serverTimeRemainingMs = timeRemObj instanceof Number ? ((Number)timeRemObj).longValue() : 0L;
                serverTimeRemainingMs = timeRemObj instanceof Number ? ((Number)timeRemObj).longValue() : 0L;

                for (Map<String,Object> p : pls) {
                    Number idn = (Number) p.getOrDefault("id", -1);
                    int id = idn == null ? -1 : idn.intValue();
                    double x = ((Number)p.getOrDefault("x", 0)).doubleValue();
                    double y = ((Number)p.getOrDefault("y", 0)).doubleValue();
                    double health = ((Number)p.getOrDefault("health", 100)).doubleValue();
                    double maxH = ((Number)p.getOrDefault("maxHealth", 100)).doubleValue();
                    double width = ((Number)p.getOrDefault("width", 60)).doubleValue();
                    double height = ((Number)p.getOrDefault("height", 30)).doubleValue();
                    int pmoney = ((Number)p.getOrDefault("money", 0)).intValue();
                    double angle = ((Number)p.getOrDefault("angle", 0)).doubleValue();
                    Object uname = p.getOrDefault("username", null);
                    Object winsObj = p.getOrDefault("wins", null);
                    Object losesObj = p.getOrDefault("loses", null);
                    Object drawsObj = p.getOrDefault("draws", null);
                    tanks.compute(id, (k, tnk) -> {
                        if (tnk == null) tnk = new Tank(x, y, k == myUserId);
                        tnk.x = x; tnk.y = y; tnk.health = health; tnk.maxHealth = maxH; tnk.width = width; tnk.height = height;
                        if (k != myUserId) tnk.angle = angle;
                        return tnk;
                    });
                    moneyById.put(id, pmoney);
                    if (uname != null) usernameById.put(id, uname.toString());
                    if (winsObj instanceof Number) winsById.put(id, ((Number)winsObj).intValue());
                    if (losesObj instanceof Number) losesById.put(id, ((Number)losesObj).intValue());
                    if (drawsObj instanceof Number) drawsById.put(id, ((Number)drawsObj).intValue());
                    try {
                        Object bonusesObj = p.get("bonuses");
                        if (bonusesObj instanceof Map) {
                            @SuppressWarnings("unchecked") Map<String,Object> bm = (Map<String,Object>) bonusesObj;
                            java.util.Map<String,Integer> parsed = new java.util.HashMap<>();
                            for (Map.Entry<String,Object> be : bm.entrySet()) {
                                try {
                                    Object bv = be.getValue();
                                    int vi = bv instanceof Number ? ((Number)bv).intValue() : Integer.parseInt(String.valueOf(bv));
                                    parsed.put(be.getKey(), vi);
                                } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed updating AuthState", ex); }
                            }
                            bonusesById.put(id, parsed);
                            if (id == myUserId) {
                                try { bonuses.clear(); bonuses.putAll(parsed); } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to apply parsed bonuses", ex); }
                                Platform.runLater(this::updateBonusesLabel);
                            }
                        }
                    } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to read bonuses map", ex); }
                    if (id == myUserId) {
                        final int fw = winsObj instanceof Number ? ((Number)winsObj).intValue() : 0;
                        final int fl = losesObj instanceof Number ? ((Number)losesObj).intValue() : 0;
                        final int fd = drawsObj instanceof Number ? ((Number)drawsObj).intValue() : 0;
                        Platform.runLater(() -> {
                            try {
                                AuthState.setWins(fw);
                                AuthState.setLoses(fl);
                                AuthState.setDraws(fd);
                            } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to set AuthState values", ex); }
                        });
                    }
                }

                bullets.clear();
                for (Map<String,Object> b : bls) {
                    Number ownerN = (Number) b.getOrDefault("owner", -1);
                    int owner = ownerN == null ? -1 : ownerN.intValue();
                    double bx = ((Number)b.getOrDefault("x", 0)).doubleValue();
                    double by = ((Number)b.getOrDefault("y", 0)).doubleValue();
                    double r = ((Number)b.getOrDefault("r", 3)).doubleValue();
                    bullets.add(new SimpleBullet(owner, bx, by, r));
                }
                Object targetsObj = m.get("targets");
                @SuppressWarnings("unchecked")
                List<Map<String,Object>> tls = targetsObj instanceof List ? (List<Map<String,Object>>) targetsObj : List.of();
                List<SimpleTarget> oldTargets = new ArrayList<>(targets);
                List<SimpleTarget> newTargets = new ArrayList<>();
                for (Map<String,Object> t : tls) {
                    double tx = ((Number)t.getOrDefault("x", 0)).doubleValue();
                    double ty = ((Number)t.getOrDefault("y", 0)).doubleValue();
                    double tr = ((Number)t.getOrDefault("r", 8)).doubleValue();
                    int th = ((Number)t.getOrDefault("health", 0)).intValue();
                    int serverMax = t.getOrDefault("maxHealth", th) instanceof Number ? ((Number)t.getOrDefault("maxHealth", th)).intValue() : th;
                    double chosenMax = serverMax;
                    SimpleTarget best = null; double bestDist = Double.MAX_VALUE;
                    for (SimpleTarget ot : oldTargets) {
                        double dx = ot.getX() - tx; double dy = ot.getY() - ty;
                        double dist = Math.hypot(dx, dy);
                        if (dist < bestDist) { bestDist = dist; best = ot; }
                    }
                    if (best != null && bestDist <= Math.max(8.0, tr * 1.5)) {
                        chosenMax = (int) Math.max(1, best.getMaxHealth());
                    }
                    newTargets.add(new SimpleTarget(tx, ty, tr, th, chosenMax));
                }
                targets.clear(); targets.addAll(newTargets);
                
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[GameController] parse error", e);
        }
    }

    private void onKeyPressed(KeyEvent e) { keys.put(e.getCode(), true); }
    private void onKeyReleased(KeyEvent e) { keys.put(e.getCode(), false); }

    private void onMousePressed(MouseEvent e) {
        if (!gameStarted) return;
        if (e.isPrimaryButtonDown()) {
            double tx = e.getX();
            double ty = e.getY();
            try {
                Tank me = tanks.get(myUserId);
                if (me != null) me.aimAt(tx, ty);
            } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to update aim on mouse move", ex); }
            Tank me = tanks.get(myUserId);
            long now = System.currentTimeMillis();
            if (me != null) {
                if (now - me.lastShotTime < me.fireRateMs) {
                    return;
                }
                me.lastShotTime = now;
            }
            try {
                String json = mapper.writeValueAsString(Map.of(
                        "type", "input",
                        "action", "shoot",
                        "tx", tx,
                        "ty", ty
                ));
                socketClient.sendText(json);
            } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to update player health UI", ex); }
            sendAimIfNeeded(tx, ty);
        }
    }

    private void onMouseMoved(MouseEvent e) {
        try {
            Tank me = tanks.get(myUserId);
            if (me != null) {
                me.aimAt(e.getX(), e.getY());
            }
        } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to update aim on mouse move", ex); }
    }

    private void onMouseDragged(MouseEvent e) {
        onMouseMoved(e);
    }

    private void sendAimIfNeeded(double tx, double ty) {
        long now = System.currentTimeMillis();
        if (now - lastAimSent < 33) return;
        lastAimSent = now;
        try {
            String json = mapper.writeValueAsString(Map.of(
                    "type", "input",
                    "action", "aim",
                    "tx", tx,
                    "ty", ty
            ));
            socketClient.sendText(json);
        } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to send aim input", ex); }
    }

    private void sendInputFromKeys() {
        double dx = 0, dy = 0;
        if (Boolean.TRUE.equals(keys.get(KeyCode.W)) || Boolean.TRUE.equals(keys.get(KeyCode.UP))) dy -= 1;
        if (Boolean.TRUE.equals(keys.get(KeyCode.S)) || Boolean.TRUE.equals(keys.get(KeyCode.DOWN))) dy += 1;
        if (Boolean.TRUE.equals(keys.get(KeyCode.A)) || Boolean.TRUE.equals(keys.get(KeyCode.LEFT))) dx -= 1;
        if (Boolean.TRUE.equals(keys.get(KeyCode.D)) || Boolean.TRUE.equals(keys.get(KeyCode.RIGHT))) dx += 1;
        try {
            String json = mapper.writeValueAsString(Map.of(
                    "type", "input",
                    "action", "move",
                    "dx", dx,
                    "dy", dy
            ));
            socketClient.sendText(json);
        } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to send move input", ex); }
    }

    private void render() {
        try {
            renderer.render(gameCanvas, tanks.values(), bullets, targets, serverMovementBarrierActive, serverStartBarrierActive, serverStartBarrierRemainingMs, myUserId);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Rendering failed", ex);
        }
    }

    private void updateUI() {
        Tank me = tanks.get(myUserId);
        Tank other = null;
        for (Map.Entry<Integer,Tank> e : tanks.entrySet()) {
            if (e.getKey() != myUserId) { other = e.getValue(); break; }
        }
        double canvasMid = (gameCanvas != null ? gameCanvas.getWidth() : 900) / 2.0;

        Integer leftId = null, rightId = null;
        for (Map.Entry<Integer, Tank> en : tanks.entrySet()) {
            Integer id = en.getKey(); Tank t = en.getValue();
            if (t == null) continue;
            if (t.x < canvasMid) {
                if (leftId == null) leftId = id;
            } else {
                if (rightId == null) rightId = id;
            }
        }

        if (leftId == null && rightId != null) {
            for (Integer k : tanks.keySet()) if (!k.equals(rightId)) { leftId = k; break; }
        }
        if (rightId == null && leftId != null) {
            for (Integer k : tanks.keySet()) if (!k.equals(leftId)) { rightId = k; break; }
        }

        double leftProg = 0, rightProg = 0;
        String leftLabel = "Player: 0", rightLabel = "Enemy: 0";
        int leftMoneyVal = 0, rightMoneyVal = 0;

        if (leftId != null) {
            Tank lt = tanks.get(leftId);
            if (lt != null) leftProg = lt.maxHealth > 0 ? lt.health / lt.maxHealth : 0;
            int leftHpVal = lt != null ? (int) Math.max(0, lt.health) : 0;
            leftLabel = (leftId == myUserId) ? "Player: " + leftHpVal : "Enemy: " + leftHpVal;
            leftMoneyVal = moneyById.getOrDefault(leftId, 0);
            if (usernameLabel != null) usernameLabel.setText(leftId == myUserId ? myUsername : usernameById.getOrDefault(leftId, "User " + leftId));
            if (playerStatsLabel != null) {
                int pw = winsById.getOrDefault(leftId, 0);
                int pl = losesById.getOrDefault(leftId, 0);
                int pd = drawsById.getOrDefault(leftId, 0);
                playerStatsLabel.setText("Wins: " + pw + " | Loses: " + pl + " | Draws: " + pd);
            }
        }
        if (rightId != null) {
            Tank rt = tanks.get(rightId);
            if (rt != null) rightProg = rt.maxHealth > 0 ? rt.health / rt.maxHealth : 0;
            int rightHpVal = rt != null ? (int) Math.max(0, rt.health) : 0;
            rightLabel = (rightId == myUserId) ? "Player: " + rightHpVal : "Enemy: " + rightHpVal;
            rightMoneyVal = moneyById.getOrDefault(rightId, 0);
            if (enemyUsernameLabel != null) enemyUsernameLabel.setText(rightId == myUserId ? myUsername : (usernameById.getOrDefault(rightId, "User " + rightId)));
            if (enemyStatsLabel != null) {
                int wv = winsById.getOrDefault(rightId, 0);
                int lv = losesById.getOrDefault(rightId, 0);
                int dv = drawsById.getOrDefault(rightId, 0);
                enemyStatsLabel.setText("Wins: " + wv + " | Loses: " + lv + " | Draws: " + dv);
            }
        }

        if (playerHealthBar != null) {
            try {
                if (leftProg <= 0) {
                    playerHealthBar.setProgress(0.0);
                    playerHealthBar.setVisible(true);
                    if (playerHealthLabel != null) playerHealthLabel.setText(leftLabel);
                } else {
                    playerHealthBar.setVisible(true);
                    playerHealthBar.setProgress(Math.max(0.0, Math.min(1.0, leftProg)));
                    if (playerHealthLabel != null) playerHealthLabel.setText(leftLabel);
                }
            } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to update enemy health UI", ex); }
        }
        if (enemyHealthBar != null) {
            try {
                if (rightProg <= 0) {
                    enemyHealthBar.setProgress(0.0);
                    enemyHealthBar.setVisible(true);
                    if (enemyHealthLabel != null) enemyHealthLabel.setText(rightLabel);
                } else {
                    enemyHealthBar.setVisible(true);
                    enemyHealthBar.setProgress(Math.max(0.0, Math.min(1.0, rightProg)));
                    if (enemyHealthLabel != null) enemyHealthLabel.setText(rightLabel);
                }
            } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to send shoot input", ex); }
        }
        if (moneyLabel != null) moneyLabel.setText("$" + leftMoneyVal);
        if (enemyMoneyLabel != null) enemyMoneyLabel.setText("$" + rightMoneyVal);
        if (timerLabel != null) {
            long rem = serverTimeRemainingMs;
            if (rem < 0) rem = 0;
            int sec = (int)(rem/1000);
            int minutes = sec/60; int seconds = sec%60;
            timerLabel.setText(String.format("%02d:%02d", minutes, seconds));
        }
        if (startCountdownLabel != null) {
            if (serverStartBarrierActive && serverStartBarrierRemainingMs > 0) {
                startCountdownLabel.setVisible(true);
                int secs = (int)((serverStartBarrierRemainingMs + 999)/1000);
                startCountdownLabel.setText(Integer.toString(secs));
            } else {
                startCountdownLabel.setVisible(false);
            }
        }
        updateBonusesLabel();
    }

    @FXML
    private void onSelectSmall() { sendSelect("SMALL"); }
    @FXML
    private void onSelectMedium() { sendSelect("MEDIUM"); }
    @FXML
    private void onSelectLarge() { sendSelect("LARGE"); }

    private void sendSelect(String type) {
        if (selectionPane != null) selectionPane.setVisible(false);
        try {
            Tank.TankType tt = Tank.TankType.valueOf(type);
            Tank me = tanks.get(myUserId);
            if (me != null) {
                me.setType(tt);
            }
        } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to apply local tank selection", ex); }
        try {
            String json = mapper.writeValueAsString(Map.of("type","input","action","select","tankType",type));
            socketClient.sendText(json);
        } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to send select input", ex); }
    }

    @FXML
    private void onRestart() {
        if (selectionPane != null) selectionPane.setVisible(true);
        bullets.clear(); tanks.clear();
        if (endPane != null) endPane.setVisible(false);
        try { bonuses.clear(); } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to clear bonuses", ex); }
        Platform.runLater(this::updateBonusesLabel);
    }

    @FXML
    private void onBackToStart(ActionEvent e) {
        try {
            Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("start-view.fxml"));
            Parent root = loader.load();
            stage.setScene(new javafx.scene.Scene(root, 900, 640));
            stage.setTitle("Tanks - Duel");
        } catch (Exception ex) {
            ErrorDialog.showError("Failed to go back", ex.getMessage(), ex);
        }
    }

    @FXML
    private void onToggleShop() {
        if (shopPane != null) {
            boolean newv = !shopPane.isVisible(); shopPane.setVisible(newv);
            if (!newv && gameCanvas != null) gameCanvas.requestFocus();
        }
    }

    @FXML
    private void onCloseShop(ActionEvent e) {
        if (shopPane != null) {
            shopPane.setVisible(false);
            if (gameCanvas != null) gameCanvas.requestFocus();
        }
    }

    @FXML
    private void onBuySpeed() { sendBuy("speed"); }
    @FXML
    private void onBuyDamage() { sendBuy("damage"); }
    @FXML
    private void onBuyHP() { sendBuy("hp"); }
    @FXML
    private void onBuyBulletSize() { sendBuy("bullet"); }

    private void sendBuy(String item) {
        try {
            String json = mapper.writeValueAsString(Map.of("type","input","action","buy","item",item));
            socketClient.sendText(json);
            try { bonuses.merge(item, 1, Integer::sum); } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to merge bonus optimistically", ex); }
            Platform.runLater(this::updateBonusesLabel);
        } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to send buy input", ex); }
    }

    private void updateBonusesLabel() {
        try {
            if (bonusesLabel == null) return;
            double canvasMid = (gameCanvas != null ? gameCanvas.getWidth() : 900) / 2.0;
            Integer leftId = null, rightId = null;
            for (Map.Entry<Integer, Tank> en : tanks.entrySet()) {
                Integer id = en.getKey(); Tank t = en.getValue();
                if (t == null) continue;
                if (t.x < canvasMid) {
                    if (leftId == null) leftId = id;
                } else {
                    if (rightId == null) rightId = id;
                }
            }
            if (leftId == null && rightId != null) {
                for (Integer k : tanks.keySet()) if (!k.equals(rightId)) { leftId = k; break; }
            }
            if (rightId == null && leftId != null) {
                for (Integer k : tanks.keySet()) if (!k.equals(leftId)) { rightId = k; break; }
            }

            Map<String,Integer> leftBon = leftId == null ? java.util.Map.of() : bonusesById.getOrDefault(leftId, java.util.Map.of());
            Map<String,Integer> rightBon = rightId == null ? java.util.Map.of() : bonusesById.getOrDefault(rightId, java.util.Map.of());

            if ((leftBon == null || leftBon.isEmpty()) && leftId != null && leftId.equals(myUserId)) leftBon = java.util.Map.copyOf(bonuses);
            if ((rightBon == null || rightBon.isEmpty()) && rightId != null && rightId.equals(myUserId)) rightBon = java.util.Map.copyOf(bonuses);

            bonusesLabel.setText(formatBonusesText(leftBon));
            if (enemyBonusesLabel != null) enemyBonusesLabel.setText(formatBonusesText(rightBon));
        } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to update bonuses label", ex); }
    }

    private String formatBonusesText(Map<String,Integer> map) {
        if (map == null || map.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(); boolean first = true;
        for (Map.Entry<String,Integer> e : map.entrySet()) {
            if (!first) sb.append("  "); first = false;
            String key = e.getKey(); int v = e.getValue();
            String human = switch (key.toLowerCase()) {
                case "speed" -> "Speed";
                case "hp" -> "HP";
                case "damage" -> "Damage";
                case "bullet" -> "Bullet";
                default -> Character.toUpperCase(key.charAt(0)) + key.substring(1);
            };
            sb.append(v).append(" x ").append(human);
        }
        return sb.toString();
    }

    
}
