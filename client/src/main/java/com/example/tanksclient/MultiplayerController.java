package com.example.tanksclient;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Optional;
import javafx.application.Platform;

public class MultiplayerController {

    @FXML
    private ListView<String> lobbyListView;

    @FXML
    private TextField searchField;

    @FXML
    private Button createLobbyButton;

    @FXML
    private Button joinLobbyButton;

    @FXML
    private Button refreshButton;

    @FXML
    private Button backButton;

    @FXML
    private VBox userInfoBox;

    @FXML
    private Label userNameLabel;

    @FXML
    private Label userStatsLabel;

    private ObservableList<String> lobbies = FXCollections.observableArrayList();
    private java.util.List<java.util.Map<String,Object>> rawLobbies = new java.util.ArrayList<>();

    @FXML
    public void initialize() {
        lobbyListView.setItems(lobbies);
        refreshLobbies();

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldV, newV) -> applyFilter());
        }

        if (userNameLabel != null) {
            userNameLabel.textProperty().bind(AuthState.usernameProperty());
        }
        if (userStatsLabel != null) {
            userStatsLabel.textProperty().bind(AuthState.winsProperty().asString("Wins: %d").concat(" | ").concat(AuthState.losesProperty().asString("Loses: %d")));
        }
        if (userInfoBox != null) {
            userInfoBox.visibleProperty().bind(AuthState.loggedInProperty());
        }
        if (createLobbyButton != null) {
            createLobbyButton.disableProperty().bind(AuthState.loggedInProperty().not());
            createLobbyButton.setTooltip(new javafx.scene.control.Tooltip("Login required to create lobbies"));
        }
        if (joinLobbyButton != null) {
            joinLobbyButton.disableProperty().bind(AuthState.loggedInProperty().not());
            joinLobbyButton.setTooltip(new javafx.scene.control.Tooltip("Login required to join lobbies"));
        }
    }

    @FXML
    private void onCreateLobby(ActionEvent e) {
        javafx.scene.control.TextField nameField = new javafx.scene.control.TextField();
        nameField.setPromptText("Enter lobby name (optional)");
        nameField.getStyleClass().add("auth-field");
        javafx.scene.control.Dialog<String> nameDialog = new javafx.scene.control.Dialog<>();
        nameDialog.setTitle("Create Lobby");
        nameDialog.setHeaderText("Lobby name (optional)");
        nameDialog.getDialogPane().getStylesheets().add(
            getClass().getResource("start.css").toExternalForm());
        nameDialog.getDialogPane().getStyleClass().add("dialog-pane");
        javafx.scene.layout.VBox nameVbox = new javafx.scene.layout.VBox(8, 
            new javafx.scene.control.Label("Name:"), nameField);
        nameVbox.setPadding(new javafx.geometry.Insets(10, 10, 10, 10));
        nameDialog.getDialogPane().setContent(nameVbox);
        nameDialog.getDialogPane().getButtonTypes().addAll(
            javafx.scene.control.ButtonType.OK,
            new javafx.scene.control.ButtonType("Skip", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE));
        nameDialog.setResultConverter(buttonType -> {
            if (buttonType == javafx.scene.control.ButtonType.OK) {
                return nameField.getText();
            }
            return null;
        });
        
        Optional<String> oname = nameDialog.showAndWait();
        String name = oname.isPresent() ? oname.get().trim() : null;
        
        javafx.scene.control.PasswordField passwordField = new javafx.scene.control.PasswordField();
        passwordField.setPromptText("Enter password (optional)");
        passwordField.getStyleClass().add("auth-field");
        javafx.scene.control.Dialog<String> passDialog = new javafx.scene.control.Dialog<>();
        passDialog.setTitle("Create Lobby");
        passDialog.setHeaderText("Lobby password (optional)");
        passDialog.getDialogPane().getStylesheets().add(
            getClass().getResource("start.css").toExternalForm());
        passDialog.getDialogPane().getStyleClass().add("dialog-pane");
        javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(8, 
            new javafx.scene.control.Label("Password:"), passwordField);
        vbox.setPadding(new javafx.geometry.Insets(10, 10, 10, 10));
        passDialog.getDialogPane().setContent(vbox);
        passDialog.getDialogPane().getButtonTypes().addAll(
            javafx.scene.control.ButtonType.OK,
            new javafx.scene.control.ButtonType("Skip", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE));
        passDialog.setResultConverter(buttonType -> {
            if (buttonType == javafx.scene.control.ButtonType.OK) {
                return passwordField.getText();
            }
            return null;
        });
        
        Optional<String> opass = passDialog.showAndWait();
        String pass = opass.isPresent() ? opass.get() : null;

        new Thread(() -> {
            try {
                String token = AuthState.getToken();
                var created = LobbyClient.createLobby(token, name == null || name.isEmpty() ? null : name, pass == null || pass.isEmpty() ? null : pass);
                Number idn = (Number) created.getOrDefault("id", -1);
                int id = idn == null ? -1 : idn.intValue();
                Platform.runLater(() -> {
                    if (id > 0) {
                        openGameView(id, token);
                    } else {
                        refreshLobbies();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> ErrorDialog.showError("Create lobby failed", ex.getMessage(), ex));
            }
        }).start();
    }

    @FXML
    private void onRefreshLobbies(ActionEvent e) {
        refreshLobbies();
    }

    private void refreshLobbies() {
        new Thread(() -> {
            try {
                var list = LobbyClient.listOpen();
                rawLobbies.clear();
                rawLobbies.addAll(list);
                Platform.runLater(this::applyFilter);
            } catch (Exception ex) {
                Platform.runLater(() -> ErrorDialog.showError("Failed to load lobbies", ex.getMessage(), ex));
            }
        }).start();
    }

    private void applyFilter() {
        String q = (searchField == null) ? "" : searchField.getText();
        String qlow = q == null ? "" : q.toLowerCase();
        lobbies.clear();
        for (var m : rawLobbies) {
            Number idn = (Number) m.getOrDefault("id", -1);
            int id = idn == null ? -1 : idn.intValue();
            String name = (String) m.getOrDefault("name", null);
            Object hostObj = m.getOrDefault("hostUsername", m.getOrDefault("hostId", "?"));
            String host = hostObj == null ? "?" : hostObj.toString();
            boolean locked = Boolean.TRUE.equals(m.getOrDefault("hasPassword", false));
            
            String displayName = (name != null && !name.trim().isEmpty()) ? name : ("Lobby #" + id);
            String lockIcon = locked ? " 🔒" : "";
            String displayText = String.format("%s%s (Host: %s)", displayName, lockIcon, host);
            
            if (qlow.isEmpty() || (name != null && name.toLowerCase().contains(qlow))) {
                lobbies.add(displayText);
            }
        }
    }

    @FXML
    private void onJoinLobby(ActionEvent e) {
        int idx = lobbyListView.getSelectionModel().getSelectedIndex();
        if (idx < 0) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setHeaderText("No lobby selected");
            a.setContentText("Select a lobby first.");
            a.showAndWait();
            return;
        }
        if (idx >= rawLobbies.size()) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setHeaderText("Invalid selection");
            a.setContentText("Selected lobby no longer exists.");
            a.showAndWait();
            return;
        }
        
        var lobbyData = rawLobbies.get(idx);
        Number idn = (Number) lobbyData.getOrDefault("id", -1);
        int id = idn == null ? -1 : idn.intValue();
        boolean locked = Boolean.TRUE.equals(lobbyData.getOrDefault("hasPassword", false));
        
        String password = null;
        if (locked) {
            javafx.scene.control.PasswordField pwdField = new javafx.scene.control.PasswordField();
            pwdField.setPromptText("Enter lobby password");
            pwdField.getStyleClass().add("auth-field");

            javafx.scene.control.Dialog<String> pwdDialog = new javafx.scene.control.Dialog<>();
            pwdDialog.setTitle("Join Lobby");
            pwdDialog.setHeaderText("Lobby password");
            try {
                var css = getClass().getResource("start.css");
                if (css != null) pwdDialog.getDialogPane().getStylesheets().add(css.toExternalForm());
            } catch (Exception ex) { java.util.logging.Logger.getLogger(MultiplayerController.class.getName()).log(java.util.logging.Level.FINE, "Failed to apply dialog css", ex); }
            pwdDialog.getDialogPane().getStyleClass().add("dialog-pane");

            javafx.scene.layout.VBox vboxPwd = new javafx.scene.layout.VBox(8,
                new javafx.scene.control.Label("Password:"), pwdField);
            vboxPwd.setPadding(new javafx.geometry.Insets(10, 10, 10, 10));
            pwdDialog.getDialogPane().setContent(vboxPwd);

            pwdDialog.getDialogPane().getButtonTypes().addAll(
                javafx.scene.control.ButtonType.OK,
                new javafx.scene.control.ButtonType("Skip", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE)
            );

            pwdDialog.setResultConverter(bt -> bt == javafx.scene.control.ButtonType.OK ? pwdField.getText() : null);

            Optional<String> op = pwdDialog.showAndWait();
            if (op.isEmpty()) return; // cancelled/skip
            password = op.get();
        }

        String finalPassword = password;
        new Thread(() -> {
            try {
                String token = AuthState.getToken();
                var joined = LobbyClient.joinLobby(id, token, finalPassword);
                Platform.runLater(() -> openGameView(id, token));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    String msg = ex == null ? "" : ex.getMessage();
                    if (msg != null && (msg.contains("403") || msg.toLowerCase().contains("invalid lobby password") || msg.toLowerCase().contains("invalid password"))) {
                        ErrorDialog.showError("Join lobby failed", "Invalid lobby password. Please try again.");
                    } else {
                        ErrorDialog.showError("Join lobby failed", msg, ex);
                    }
                });
            }
        }).start();
    }

    private void openGameView(int lobbyId, String token) {
        try {
            FXMLLoader loader = Navigation.loadAndSetScene(lobbyListView, "game-view.fxml", 900, 640, "Tanks - Multiplayer (Lobby " + lobbyId + ")");
            GameController gc = loader.getController();
            if (gc != null) gc.init(lobbyId, token);
        } catch (Exception ex) {
            ErrorDialog.showError("Failed to open game view", ex.toString(), ex);
        }
    }

    @FXML
    private void onBack(ActionEvent e) {
        try {
            Navigation.loadAndSetScene((Node) e.getSource(), "start-view.fxml", 900, 640, "Tanks - Duel");
        } catch (Exception ex) {
            ErrorDialog.showError("Failed to go back", ex.getMessage(), ex);
        }
    }

    @FXML
    private void onExit(ActionEvent e) {
        Platform.exit();
    }
}
