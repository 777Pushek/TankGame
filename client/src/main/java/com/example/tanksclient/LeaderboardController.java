package com.example.tanksclient;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Value;

public class LeaderboardController {

    @FXML private TableView<LeaderboardRow> table;
    @FXML private TableColumn<LeaderboardRow, Integer> colRank;
    @FXML private TableColumn<LeaderboardRow, String> colUser;
    @FXML private TableColumn<LeaderboardRow, Integer> colWins;
    @FXML private TableColumn<LeaderboardRow, Integer> colLoses;
    @FXML private TableColumn<LeaderboardRow, Integer> colDraws;
    @FXML private TableColumn<LeaderboardRow, String> colMost;

    @FXML
    public void initialize() {
        colRank.setCellValueFactory(new PropertyValueFactory<>("rank"));
        colUser.setCellValueFactory(new PropertyValueFactory<>("username"));
        colWins.setCellValueFactory(new PropertyValueFactory<>("wins"));
        colLoses.setCellValueFactory(new PropertyValueFactory<>("loses"));
        colDraws.setCellValueFactory(new PropertyValueFactory<>("draws"));
        colMost.setCellValueFactory(new PropertyValueFactory<>("mostUsedTank"));

        new Thread(this::loadLeaderboard).start();
    }

    private void loadLeaderboard() {
        try {
            List<Map<String,Object>> raw = LeaderboardClient.fetchLeaderboard();
            List<LeaderboardRow> rows = new ArrayList<>();
            int limit = 10;
            int count = Math.min(raw == null ? 0 : raw.size(), limit);
            for (int i = 0; i < count; i++) {
                Map<String,Object> m = raw.get(i);
                String username = m.getOrDefault("username", "").toString();
                Object ow = m.get("wins");
                Object ol = m.get("loses");
                Object od = m.get("draws");
                int wins = ow instanceof Number ? ((Number)ow).intValue() : 0;
                int loses = ol instanceof Number ? ((Number)ol).intValue() : 0;
                int draws = od instanceof Number ? ((Number)od).intValue() : 0;
                String most = m.getOrDefault("mostUsedTank", "").toString();
                rows.add(new LeaderboardRow(i + 1, username, wins, loses, draws, most));
            }
            ObservableList<LeaderboardRow> obs = FXCollections.observableArrayList(rows);
            double fixedCell = 42.0;
            double header = 35.0;
            double prefHeight = (10 * fixedCell) + header;
            Platform.runLater(() -> {
                table.setFixedCellSize(fixedCell);
                table.setItems(obs);
                table.setPrefWidth(780);
                table.setMaxWidth(780);
                table.setPrefHeight(prefHeight);
                table.setMaxHeight(prefHeight);
                table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
                table.setPlaceholder(new Label("No leaderboard entries"));
            });
        } catch (IOException | InterruptedException e) {
            Platform.runLater(() -> {
                table.setItems(FXCollections.observableArrayList());
                ErrorDialog.showError("Failed to load leaderboard", e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage()), e);
            });
        }
    }

    @FXML
    private void onBack(javafx.event.ActionEvent ev) {
        try {
            Navigation.loadAndSetScene((Node) ev.getSource(), "start-view.fxml", 900, 640, "Tanks - Duel");
        } catch (Exception ex) {
            ErrorDialog.showError("Navigation error", "Failed to navigate to the main menu", ex);
        }
    }

    @Value
    public static class LeaderboardRow {
        int rank;
        String username;
        int wins;
        int loses;
        int draws;
        String mostUsedTank;
    }
}
