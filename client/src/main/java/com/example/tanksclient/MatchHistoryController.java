package com.example.tanksclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableRow;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.Value;

public class MatchHistoryController {

    private static final Logger LOGGER = Logger.getLogger(MatchHistoryController.class.getName());

    @FXML
    private TableView<MatchRow> table;
    @FXML private TableColumn<MatchRow, String> colStart;
    @FXML private TableColumn<MatchRow, String> colOpponent;
    @FXML private TableColumn<MatchRow, String> colOpponentTank;
    @FXML private TableColumn<MatchRow, String> colTank;
    @FXML private TableColumn<MatchRow, String> colTimeRem;
    @FXML private TableColumn<MatchRow, String> colResult;

    private final DateTimeFormatter dtf = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @FXML
    public void initialize() {
        colStart.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
            cd.getValue().getStart() == null ? "" : cd.getValue().getStart().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        ));

        colOpponent.setCellValueFactory(new PropertyValueFactory<>("opponent"));
        colOpponentTank.setCellValueFactory(new PropertyValueFactory<>("opponentTank"));
        colTank.setCellValueFactory(new PropertyValueFactory<>("tankType"));
        colTimeRem.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
            formatMillisAsMinutesSeconds(cd.getValue().getTimeRemainingMs())
        ));
        colResult.setCellValueFactory(new PropertyValueFactory<>("result"));

        currentPage = 0;
        pageSize = 10;

        table.setFixedCellSize(42.0);

        table.setRowFactory(tv -> new TableRow<MatchRow>() {
            @Override
            protected void updateItem(MatchRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("match-win", "match-lose", "match-draw");
                
                if (!empty && item != null) {
                    String res = item.getResult() == null ? "" : item.getResult().toUpperCase();
                    switch (res) {
                        case "WIN" -> getStyleClass().add("match-win");
                        case "LOSE" -> getStyleClass().add("match-lose");
                        case "DRAW" -> getStyleClass().add("match-draw");
                    }
                }
            }
        });

        new Thread(() -> loadMatchesPage(currentPage)).start();
    }

    private String formatMillisAsMinutesSeconds(long ms) {
        if (ms <= 0) return "0:00";
        long totalSec = ms / 1000;
        long mins = totalSec / 60;
        long secs = totalSec % 60;
        return String.format("%d:%02d", mins, secs);
    }

    private int currentPage = 0;
    private int pageSize = 10;
    private int totalPages = 0;

    private void loadMatchesPage(int page) {
        try {
            String token = AuthState.getToken();
            Map<String,Object> resp = MatchClient.fetchMyMatchesPage(token, page, pageSize);
            if (resp == null) resp = Map.of();
            Object contentObj = resp.getOrDefault("content", List.of());
            List<Map<String,Object>> raw = (List<Map<String,Object>>) contentObj;
            Number tp = (Number) resp.getOrDefault("totalPages", 0);
            Number te = (Number) resp.getOrDefault("totalElements", 0);
            totalPages = tp == null ? 0 : tp.intValue();
            List<MatchRow> rows = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper();
            for (Map<String,Object> m : raw) {
                String startStr = m.getOrDefault("startTime", "").toString();
                String startFormatted = startStr;
                LocalDateTime start = null;
                try {
                    if (startStr != null && !startStr.isBlank()) {
                        start = LocalDateTime.parse(startStr);
                        startFormatted = start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    }
                } catch (Exception ex) { LOGGER.log(Level.FINE, "Failed to parse start time", ex); }

                Object oDur = m.get("durationMs");
                long duration = oDur instanceof Number ? ((Number)oDur).longValue() : 0L;
                String opponent = m.getOrDefault("opponentUsername", "").toString();
                String opponentTank = m.getOrDefault("opponentTank", "").toString();
                String tank = m.getOrDefault("tankType", "").toString();
                Object oTime = m.get("timeRemainingMs");
                long timeRem = oTime instanceof Number ? ((Number)oTime).longValue() : 0L;
                String result = m.getOrDefault("result", "").toString();

                rows.add(new MatchRow(startFormatted, start, 0, opponent, opponentTank, tank, timeRem, result));
            }

            ObservableList<MatchRow> obs = FXCollections.observableArrayList(rows);
            Platform.runLater(() -> {
                table.setItems(obs);
                pageInfoLabel.setText("Page " + (currentPage + 1) + " of " + Math.max(1, totalPages));
                prevPageButton.setDisable(currentPage <= 0);
                nextPageButton.setDisable(currentPage >= Math.max(0, totalPages - 1));
            });
        } catch (IOException | InterruptedException e) {
            Platform.runLater(() -> {
                table.setItems(FXCollections.observableArrayList());
                ErrorDialog.showError("Failed to load match history", e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage()), e);
            });
        }
    }

    @FXML private javafx.scene.control.Button prevPageButton;
    @FXML private javafx.scene.control.Button nextPageButton;
    @FXML private javafx.scene.control.Label pageInfoLabel;

    @FXML
    private void onPrevPage() {
        if (currentPage > 0) {
            currentPage--;
            new Thread(() -> loadMatchesPage(currentPage)).start();
        }
    }

    @FXML
    private void onNextPage() {
        if (currentPage < Math.max(0, totalPages - 1)) {
            currentPage++;
            new Thread(() -> loadMatchesPage(currentPage)).start();
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
    public static class MatchRow {
        String startTime;
        LocalDateTime start;
        long durationMs;
        String opponent;
        String opponentTank;
        String tankType;
        long timeRemainingMs;
        String result;
    }
}
