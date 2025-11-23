package com.example.projekt.websocket;

import java.util.HashMap;
import java.util.Map;

public class PlayerState {
    public final int id;
    public boolean selected = false;
    public String tankType = "MEDIUM";
    
    public int side = 0;
    public double x = 100;
    public double y = 100;
    public double vx = 0;
    public double vy = 0;
    
    public double angle = 0.0;
    public double speed = 150; 
    public double health = 100;
    public double maxHealth = 100;
    public double width = 40;
    public double height = 30;
    public int damage = 20;
    public double bulletRadius = 4;
    
    public long fireRateMs = 400;
    
    public long lastShotTime = 0L;
    public int money = 0;
    public Map<String,Integer> bonuses = new HashMap<>();

    public PlayerState(int id) { this.id = id; }
}
