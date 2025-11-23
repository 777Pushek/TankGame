package com.example.projekt.websocket;

public class Bullet {
    public final int ownerId;
    public double x, y;
    public double vx, vy;
    public int damage;
    public double radius;

    public Bullet(int ownerId, double x, double y, double vx, double vy, int damage, double radius) {
        this.ownerId = ownerId;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.damage = damage;
        this.radius = radius;
    }
}
