package com.example.tanksclient;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class Bullet {
    public double x, y;
    public double vx, vy;
    public double speed = 400;
    public double radius = 4;
    public boolean ownerIsPlayer;
    public int damage = 10;

    public Bullet(double x, double y, double dir, boolean ownerIsPlayer, int damage, double radius) {
        this.x = x;
        this.y = y;
        this.vx = Math.cos(dir) * speed;
        this.vy = Math.sin(dir) * speed;
        this.ownerIsPlayer = ownerIsPlayer;
        this.damage = damage;
        this.radius = radius;
    }

    public void update(double dt) {
        x += vx * dt;
        y += vy * dt;
    }

    public void render(GraphicsContext gc) {
        gc.setFill(ownerIsPlayer ? Color.YELLOW : Color.ORANGE);
        gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);
    }

    public boolean isOutOfBounds(double w, double h) {
        return x < -20 || y < -20 || x > w + 20 || y > h + 20;
    }

    public boolean hits(Tank t) {
        double dx = x - t.x;
        double dy = y - t.y;
        double dist2 = dx * dx + dy * dy;
        double r = Math.max(t.width, t.height) / 2.0;
        return dist2 <= (r + radius) * (r + radius);
    }
}
