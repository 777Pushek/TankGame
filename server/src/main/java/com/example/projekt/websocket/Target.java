package com.example.projekt.websocket;

public class Target {
    public double x, y;
    public double vy;
    public int health;
    public int maxHealth;
    public double radius;
    public Target(double x, double y, double vy, int health, int unusedDamage, double radius) {
        this.x = x; this.y = y; this.vy = vy == 0 ? 80 : vy; this.health = health; this.maxHealth = health; this.radius = radius;
    }
}
