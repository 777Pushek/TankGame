package com.example.tanksclient;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GameRenderer {
    private static final Logger LOGGER = Logger.getLogger(GameRenderer.class.getName());
    public void render(Canvas gameCanvas,
                       Collection<Tank> tanks,
                       List<SimpleBullet> bullets,
                       List<SimpleTarget> targets,
                       boolean serverMovementBarrierActive,
                       boolean serverStartBarrierActive,
                       long serverStartBarrierRemainingMs,
                       int myUserId) {
        GraphicsContext gc = gameCanvas.getGraphicsContext2D();
        double w = gameCanvas.getWidth();
        double h = gameCanvas.getHeight();
        gc.setFill(javafx.scene.paint.Color.web("#333"));
        gc.fillRect(0,0,w,h);

        gc.setStroke(javafx.scene.paint.Color.GRAY);
        gc.strokeLine(w/2,0,w/2,h);

        double barrierW = 20;
        double barrierX = w/2.0 - barrierW/2.0;
        double barrierY = 0;
        double barrierH = h;
        if (serverMovementBarrierActive) {
            gc.setFill(javafx.scene.paint.Color.color(0.1,0.1,0.4,0.35));
            gc.fillRect(barrierX, barrierY, barrierW, barrierH);
            gc.setStroke(javafx.scene.paint.Color.DARKBLUE);
            gc.strokeRect(barrierX, barrierY, barrierW, barrierH);
        }

        if (serverStartBarrierActive && serverStartBarrierRemainingMs > 0) {
            gc.setFill(javafx.scene.paint.Color.color(0.2,0.4,1.0,0.45));
            gc.fillRect(barrierX, barrierY, barrierW, barrierH);
            gc.setStroke(javafx.scene.paint.Color.LIGHTBLUE);
            gc.strokeRect(barrierX, barrierY, barrierW, barrierH);
        }

        for (SimpleTarget t : targets) {
            gc.setFill(javafx.scene.paint.Color.DARKGOLDENROD);
            gc.fillOval(t.getX() - t.getR(), t.getY() - t.getR(), t.getR() * 2, t.getR() * 2);
            double barX = t.getX() - t.getR();
            double barY = t.getY() - t.getR() - 6;
            double barW = t.getR() * 2;
            double barH = 4;
            gc.setFill(javafx.scene.paint.Color.DIMGRAY);
            gc.fillRect(barX, barY, barW, barH);
            double maxH = t.getMaxHealth() <= 0 ? 100.0 : t.getMaxHealth();
            double frac = Math.max(0.0, Math.min(1.0, t.getHealth() / maxH));
            gc.setFill(javafx.scene.paint.Color.LIMEGREEN);
            gc.fillRect(barX, barY, barW * frac, barH);
            gc.setStroke(javafx.scene.paint.Color.BLACK);
            gc.strokeRect(barX, barY, barW, barH);
            try {
                double fontSize = Math.max(10, t.getR() * 1.2);
                gc.setFill(javafx.scene.paint.Color.GREEN);
                gc.setFont(Font.font("System", FontWeight.BOLD, fontSize));
                javafx.geometry.VPos prev = gc.getTextBaseline();
                gc.setTextBaseline(javafx.geometry.VPos.CENTER);
                gc.fillText("$", t.getX() - (fontSize / 4.0), t.getY());
                gc.setTextBaseline(prev);
            } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed drawing target overlay", ex); }
        }

        for (Tank t : tanks) t.render(gc);

        for (SimpleBullet b : bullets) {
            gc.setFill(b.getOwner() == myUserId ? javafx.scene.paint.Color.YELLOW : javafx.scene.paint.Color.ORANGE);
            gc.fillOval(b.getX() - b.getR(), b.getY() - b.getR(), b.getR() * 2, b.getR() * 2);
        }
    }
}
