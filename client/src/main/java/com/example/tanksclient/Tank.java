package com.example.tanksclient;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class Tank {
    public double x, y;
    public double width = 60, height = 30;
    public double angle = 0;
    public double speed = 150;
    public double health = 100;
    public double maxHealth = 100;
    public int damage = 10;
    public boolean isPlayer;
    public TankType type = TankType.MEDIUM;
    public double bulletRadius = 4;
    public long fireRateMs = 400;
    public long lastShotTime = 0L;

    public Tank(double x, double y, boolean isPlayer) {
        this.x = x;
        this.y = y;
        this.isPlayer = isPlayer;
    }

    public enum TankType {
        SMALL(40, 20, 220, 70, 8, 3),
        MEDIUM(60, 30, 150, 100, 12, 5),
        LARGE(80, 40, 100, 150, 20, 8);

        public final double width, height, speed, maxHealth;
        public final int damage;
        public final double bulletRadius;

        TankType(double width, double height, double speed, double maxHealth, int damage, double bulletRadius) {
            this.width = width;
            this.height = height;
            this.speed = speed;
            this.maxHealth = maxHealth;
            this.damage = damage;
            this.bulletRadius = bulletRadius;
        }
    }

    public void setType(TankType t) {
        this.type = t;
        this.width = t.width;
        this.height = t.height;
        this.speed = t.speed;
        this.maxHealth = t.maxHealth;
        this.damage = t.damage;
        this.health = this.maxHealth;
        this.bulletRadius = t.bulletRadius;
        switch (t) {
            case SMALL -> this.fireRateMs = 200;
            case LARGE -> this.fireRateMs = 700;
            default -> this.fireRateMs = 400;
        }
        this.lastShotTime = 0L;
    }

    public void aimAt(double tx, double ty) {
        angle = Math.atan2(ty - y, tx - x);
    }

    public void render(GraphicsContext gc) {
        gc.save();
        gc.translate(x, y);
        gc.setFill(isPlayer ? Color.DARKGREEN : Color.DARKRED);
        gc.fillRect(-width / 2, -height / 2, width, height);
        gc.setFill(Color.GRAY);
        gc.fillOval(-10, -10, 20, 20);
        gc.setStroke(Color.DARKSLATEGRAY);
        gc.setLineWidth(6);
        double bx = Math.cos(angle) * 40;
        double by = Math.sin(angle) * 40;
        gc.strokeLine(0, 0, bx, by);
        gc.restore();
    }

    public Bullet shootAt(double tx, double ty) {
        double dir = Math.atan2(ty - y, tx - x);
        double bx = x + Math.cos(dir) * (Math.max(width, height) / 2 + 10);
        double by = y + Math.sin(dir) * (Math.max(width, height) / 2 + 10);
        Bullet b = new Bullet(bx, by, dir, isPlayer, this.damage, this.bulletRadius);
        return b;
    }
}
