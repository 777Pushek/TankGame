package com.example.tanksclient;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FallingTarget {
    private static final Logger LOGGER = Logger.getLogger(FallingTarget.class.getName());
    public double x, y;
    public double radius = 18;
    public double health = 50;
    public double speed = 80;

    public FallingTarget(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void update(double dt) {
        y += speed * dt;
    }

    public void render(GraphicsContext gc) {
        gc.setFill(Color.DARKGOLDENROD);
        gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        
        try {
            double fontSize = Math.max(10, radius * 1.2);
            gc.setFill(Color.GREEN);
            gc.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, fontSize));
            javafx.geometry.VPos prev = gc.getTextBaseline();
            gc.setTextBaseline(javafx.geometry.VPos.CENTER);
            gc.fillText("$", x - (fontSize/4.0), y);
            gc.setTextBaseline(prev);
        } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to draw target $", ex); }

        double barW = radius * 2;
        double barH = 5;
        double bx = x - radius;
        double by = y - radius - 10;
        gc.setFill(Color.DIMGRAY);
        gc.fillRect(bx, by, barW, barH);
        gc.setFill(Color.LIMEGREEN);
        double pw = Math.max(0, Math.min(1.0, health / 50.0)) * barW;
        gc.fillRect(bx, by, pw, barH);
        gc.setStroke(Color.BLACK);
        gc.strokeRect(bx, by, barW, barH);
    }

    public boolean hits(double bx, double by) {
        double dx = bx - x;
        double dy = by - y;
        return dx * dx + dy * dy <= (radius) * (radius);
    }
}
