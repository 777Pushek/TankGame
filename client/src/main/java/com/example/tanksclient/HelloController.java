package com.example.tanksclient;

import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.event.ActionEvent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HelloController {
    private static final Logger LOGGER = Logger.getLogger(HelloController.class.getName());
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
    private Label timerLabel;

    @FXML
    private VBox selectionPane;

    @FXML
    private Label moneyLabel;
    @FXML
    private Label bonusesLabel;
    
    @FXML
    private Label usernameLabel;
    @FXML
    private Label enemyUsernameLabel;
    @FXML
    private Label enemyMoneyLabel;
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

    private boolean barrierActive = false;
    private long barrierEndTime = 0L;

    @FXML
    private VBox endPane;

    @FXML
    private Label endMessageLabel;

    private int playerMoney = 0;

    private final Map<String,Integer> bonuses = new ConcurrentHashMap<>();

    private GraphicsContext gc;

    private Tank player;
    private Tank enemy;
    private List<Bullet> bullets = new ArrayList<>();
    private List<FallingTarget> fallingTargets = new ArrayList<>();
    private long lastTargetSpawn = 0L;
    private long nextTargetDelay = 0L; // ms

    private boolean up, down, left, right;
    private double mouseX, mouseY;

    private long gameStartTime;
    private final long GAME_DURATION_MS = 3 * 60 * 1000; // 3 minutes

    private AnimationTimer gameLoop;
    private Random random = new Random();

    @FXML
    public void initialize() {
        gc = gameCanvas.getGraphicsContext2D();

        player = new Tank(100, gameCanvas.getHeight() / 2.0, true);
        enemy = new Tank(gameCanvas.getWidth() - 140, gameCanvas.getHeight() / 2.0, false);

    enemy.setType(Tank.TankType.MEDIUM);

    playerMoney = 0;
    if (moneyLabel != null) moneyLabel.setText("$" + playerMoney);

    scheduleNextFallingTarget();

    gameStartTime = System.currentTimeMillis();

    setupGameLoop();

        gameCanvas.setOnMouseMoved(this::onMouseMoved);
        gameCanvas.setOnMouseDragged(this::onMouseMoved);
        gameCanvas.setOnMousePressed(this::onMousePressed);

        gameCanvas.setFocusTraversable(true);
        gameCanvas.addEventHandler(KeyEvent.KEY_PRESSED, e -> onKeyPressed(e));
        gameCanvas.addEventHandler(KeyEvent.KEY_RELEASED, e -> onKeyReleased(e));

        if (selectionPane != null) selectionPane.setVisible(true);
        if (shopButton != null) shopButton.setFocusTraversable(false);
    }

    private void setupGameLoop() {
        gameLoop = new AnimationTimer() {
            private long last = 0;

            @Override
            public void handle(long now) {
                if (last == 0) last = now;
                double delta = (now - last) / 1_000_000_000.0; // seconds
                last = now;

                update(delta);
                render();
                updateUI();
            }
        };
    }

    private void update(double dt) {
        double mv = player.speed * dt;
        if (up) player.y -= mv;
        if (down) player.y += mv;
        if (left) player.x -= mv;
        if (right) player.x += mv;

        constrainToCanvas(player);
        constrainToCanvas(enemy);

        player.aimAt(mouseX, mouseY);
        
        enemy.aimAt(player.x, player.y);

        if (random.nextDouble() < 0.01) {
            if (random.nextBoolean()) enemy.y += 20;
            else enemy.y -= 20;
        }
        enemy.x = Math.max(gameCanvas.getWidth() - 200, Math.min(gameCanvas.getWidth() - 80, enemy.x));

        if (random.nextDouble() < 0.01) {
            Bullet b = enemy.shootAt(player.x, player.y);
            if (b != null) bullets.add(b);
        }

        double w = gameCanvas.getWidth();
        double h = gameCanvas.getHeight();
        double barrierW = 20;
        double barrierX = w / 2.0 - barrierW / 2.0;
        double barrierY = 0;
        double barrierH = h;

        Iterator<FallingTarget> ftIt = fallingTargets.iterator();
        while (ftIt.hasNext()) {
            FallingTarget ft = ftIt.next();
            ft.update(dt);
            if (ft.y - ft.radius > h) {
                ftIt.remove();
            }
        }

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastTargetSpawn >= nextTargetDelay) {
            spawnFallingTarget();
            scheduleNextFallingTarget();
        }

        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext()) {
            Bullet b = it.next();
            b.update(dt);
            boolean collidedWithTarget = false;
            Iterator<FallingTarget> tIt = fallingTargets.iterator();
            while (tIt.hasNext()) {
                FallingTarget ft = tIt.next();
                if (ft.hits(b.x, b.y)) {
                    ft.health -= b.damage;
                    if (b.ownerIsPlayer && ft.health <= 0) {
                        playerMoney += 50;
                    }
                    it.remove();
                    collidedWithTarget = true;
                    if (ft.health <= 0) {
                        tIt.remove();
                        scheduleNextFallingTarget();
                    }
                    break;
                }
            }
            if (collidedWithTarget) continue;

            if (barrierActive) {
                if (b.x >= barrierX && b.x <= barrierX + barrierW && b.y >= barrierY && b.y <= barrierY + barrierH) {
                    it.remove();
                    continue;
                }
            }

            if (b.ownerIsPlayer) {
                if (b.hits(enemy)) {
                    enemy.health -= b.damage;
                    it.remove();
                    continue;
                }
            } else {
                if (b.hits(player)) {
                    player.health -= b.damage;
                    it.remove();
                    continue;
                }
            }

            if (b.isOutOfBounds(w, h)) {
                it.remove();
            }
        }

        if (barrierActive) {
            long now = System.currentTimeMillis();
            long remaining = barrierEndTime - now;
            if (remaining <= 0) {
                barrierActive = false;
                if (startCountdownLabel != null) startCountdownLabel.setVisible(false);
            } else {
                if (startCountdownLabel != null) {
                    int secs = (int) ((remaining + 999) / 1000);
                    startCountdownLabel.setText(Integer.toString(secs));
                    startCountdownLabel.setVisible(true);
                }
            }
        }

    player.health = Math.max(0, Math.min(player.maxHealth, player.health));
    enemy.health = Math.max(0, Math.min(enemy.maxHealth, enemy.health));

        long elapsed = System.currentTimeMillis() - gameStartTime;
        if (elapsed >= GAME_DURATION_MS) {
            stopGame("DRAW");
        } else if (player.health <= 0 && enemy.health <= 0) {
            stopGame("DRAW");
        } else if (player.health <= 0) {
            stopGame("YOU LOSE");
        } else if (enemy.health <= 0) {
            stopGame("YOU WIN");
        }
    }

    private void render() {
        double w = gameCanvas.getWidth();
        double h = gameCanvas.getHeight();
        gc.setFill(Color.web("#333"));
        gc.fillRect(0, 0, w, h);

        gc.setStroke(Color.GRAY);
        gc.strokeLine(w / 2, 0, w / 2, h);

        double barrierW = 20;
        double barrierX = w / 2.0 - barrierW / 2.0;
        double barrierY = 0;
        double barrierH = h;
        
        gc.setFill(Color.color(0.1, 0.1, 0.4, 0.35));
        gc.fillRect(barrierX, barrierY, barrierW, barrierH);
        gc.setStroke(Color.DARKBLUE);
        gc.strokeRect(barrierX, barrierY, barrierW, barrierH);
        
        if (barrierActive) {
            gc.setFill(Color.color(0.2, 0.4, 1.0, 0.45));
            gc.fillRect(barrierX, barrierY, barrierW, barrierH);
            gc.setStroke(Color.LIGHTBLUE);
            gc.strokeRect(barrierX, barrierY, barrierW, barrierH);
        }

        for (FallingTarget ft : fallingTargets) ft.render(gc);

        player.render(gc);
        enemy.render(gc);

        for (Bullet b : bullets) b.render(gc);
    }

    private void updateUI() {
        playerHealthBar.setProgress(player.maxHealth > 0 ? player.health / player.maxHealth : 0);
        enemyHealthBar.setProgress(enemy.maxHealth > 0 ? enemy.health / enemy.maxHealth : 0);
        playerHealthLabel.setText("Player: " + (int) player.health);
        enemyHealthLabel.setText("Enemy: " + (int) enemy.health);

        long remaining = GAME_DURATION_MS - (System.currentTimeMillis() - gameStartTime);
        if (remaining < 0) remaining = 0;
        int sec = (int) (remaining / 1000);
        int minutes = sec / 60;
        int seconds = sec % 60;
        timerLabel.setText(String.format("%02d:%02d", minutes, seconds));
        if (moneyLabel != null) moneyLabel.setText("$" + playerMoney);
    }

    private void scheduleNextFallingTarget() {
        lastTargetSpawn = System.currentTimeMillis();
        nextTargetDelay = 5000 + random.nextInt(10000); // 5-15 seconds
    }

    private void spawnFallingTarget() {
        double w = gameCanvas.getWidth();
        double x = 40 + random.nextDouble() * (w - 80);
        FallingTarget ft = new FallingTarget(x, -20);
        ft.health = 50;
        fallingTargets.add(ft);
    }

    private void stopGame(String message) {
        gameLoop.stop();
        showEndMessage(message);
    }

    private void showEndMessage(String message) {
        if (endMessageLabel != null) {
            endMessageLabel.setText(message);
            if ("YOU WIN".equals(message)) {
                endMessageLabel.setStyle("-fx-text-fill: lime; -fx-font-size:48px; -fx-font-weight:bold;");
            } else if ("YOU LOSE".equals(message)) {
                endMessageLabel.setStyle("-fx-text-fill: red; -fx-font-size:48px; -fx-font-weight:bold;");
            } else {
                endMessageLabel.setStyle("-fx-text-fill: white; -fx-font-size:36px; -fx-font-weight:bold;");
            }
        }
        if (endPane != null) endPane.setVisible(true);
        if (startCountdownLabel != null) startCountdownLabel.setVisible(false);
    }

    @FXML
    private void onBackToStart(ActionEvent e) {
        try {
            Navigation.loadAndSetScene((javafx.scene.Node) e.getSource(), "start-view.fxml", 820, 640, "Tanks - Main Menu");
        } catch (Exception ex) {
            ErrorDialog.showError("Navigation error", "Unable to load main menu", ex);
        }
    }

    @FXML
    private void onToggleShop(ActionEvent e) {
        if (shopPane != null) {
            boolean newVis = !shopPane.isVisible();
            shopPane.setVisible(newVis);
            if (!newVis && gameCanvas != null) gameCanvas.requestFocus();
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
    private void onBuySpeed(ActionEvent e) {
        final int cost = 50;
        if (playerMoney >= cost) {
            playerMoney -= cost;
            player.speed += 30;
            if (shopPane != null) shopPane.setVisible(false);
            if (gameCanvas != null) gameCanvas.requestFocus();
            bonuses.merge("speed", 1, Integer::sum);
            updateBonusesLabel();
        }
    }

    @FXML
    private void onBuyDamage(ActionEvent e) {
        final int cost = 50;
        if (playerMoney >= cost) {
            playerMoney -= cost;
            player.damage += 5;
            if (shopPane != null) shopPane.setVisible(false);
            if (gameCanvas != null) gameCanvas.requestFocus();
            bonuses.merge("damage", 1, Integer::sum);
            updateBonusesLabel();
        }
    }

    @FXML
    private void onBuyHP(ActionEvent e) {
        final int cost = 50;
        if (playerMoney >= cost) {
            playerMoney -= cost;
            player.maxHealth += 20;
            player.health = Math.min(player.maxHealth, player.health + 20);
            if (shopPane != null) shopPane.setVisible(false);
            if (gameCanvas != null) gameCanvas.requestFocus();
            bonuses.merge("hp", 1, Integer::sum);
            updateBonusesLabel();
        }
    }

    @FXML
    private void onBuyBulletSize(ActionEvent e) {
        final int cost = 50;
        if (playerMoney >= cost) {
            playerMoney -= cost;
            player.bulletRadius += 1.0;
            if (shopPane != null) shopPane.setVisible(false);
            if (gameCanvas != null) gameCanvas.requestFocus();
            bonuses.merge("bullet", 1, Integer::sum);
            updateBonusesLabel();
        }
    }

    private void updateBonusesLabel() {
        try {
            if (bonusesLabel == null) return;
            if (bonuses.isEmpty()) { bonusesLabel.setText(""); return; }
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String,Integer> e : bonuses.entrySet()) {
                if (!first) sb.append("  ");
                first = false;
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
            bonusesLabel.setText(sb.toString());
        } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to update bonuses label", ex); }
    }

    private void constrainToCanvas(Tank t) {
        double w = gameCanvas.getWidth();
        double h = gameCanvas.getHeight();
        double halfW = t.width / 2.0;
        double halfH = t.height / 2.0;

        double minX = halfW;
        double maxX = w - halfW;
        double minY = halfH;
        double maxY = h - halfH;

        double barrierX = w / 2.0;
        if (t.isPlayer) {
            maxX = Math.min(maxX, barrierX - halfW);
        } else {
            minX = Math.max(minX, barrierX + halfW);
        }

        t.x = Math.max(minX, Math.min(maxX, t.x));
        t.y = Math.max(minY, Math.min(maxY, t.y));
    }

    private void onMouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    private void onMousePressed(MouseEvent e) {
        if (e.isPrimaryButtonDown()) {
            Bullet b = player.shootAt(mouseX, mouseY);
            if (b != null) bullets.add(b);
        }
    }

    @FXML
    private void onSelectSmall(ActionEvent e) {
        selectPlayerType(Tank.TankType.SMALL);
    }

    @FXML
    private void onSelectMedium(ActionEvent e) {
        selectPlayerType(Tank.TankType.MEDIUM);
    }

    @FXML
    private void onSelectLarge(ActionEvent e) {
        selectPlayerType(Tank.TankType.LARGE);
    }

    private void selectPlayerType(Tank.TankType type) {
        player.setType(type);
        if (enemy.type == null) enemy.setType(Tank.TankType.MEDIUM);

        if (selectionPane != null) selectionPane.setVisible(false);
        gameStartTime = System.currentTimeMillis();
        barrierActive = true;
        barrierEndTime = System.currentTimeMillis() + 5000;
        if (startCountdownLabel != null) {
            startCountdownLabel.setText("5");
            startCountdownLabel.setVisible(true);
        }
        gameCanvas.requestFocus();
        gameLoop.start();
    }

    private void onKeyPressed(KeyEvent e) {
        if (e.getCode() == KeyCode.W || e.getCode() == KeyCode.UP) up = true;
        if (e.getCode() == KeyCode.S || e.getCode() == KeyCode.DOWN) down = true;
        if (e.getCode() == KeyCode.A || e.getCode() == KeyCode.LEFT) left = true;
        if (e.getCode() == KeyCode.D || e.getCode() == KeyCode.RIGHT) right = true;
    }

    private void onKeyReleased(KeyEvent e) {
        if (e.getCode() == KeyCode.W || e.getCode() == KeyCode.UP) up = false;
        if (e.getCode() == KeyCode.S || e.getCode() == KeyCode.DOWN) down = false;
        if (e.getCode() == KeyCode.A || e.getCode() == KeyCode.LEFT) left = false;
        if (e.getCode() == KeyCode.D || e.getCode() == KeyCode.RIGHT) right = false;
    }
}

