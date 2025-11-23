package com.example.tanksclient;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.beans.binding.Bindings;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import java.io.IOException;

public class StartController {

    @FXML
    private VBox authBox;

    @FXML
    private Label authModeLabel;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    private boolean authIsRegister = false;

    @FXML
    private VBox userInfoBox;

    @FXML
    private Label userNameLabel;

    @FXML
    private Label userStatsLabel;

    @FXML
    private Button loginButton;

    @FXML
    private Button registerButton;

    @FXML
    private Button logoutButton;

    @FXML
    private Button matchHistoryButton;

    @FXML
    public void initialize() {
        userNameLabel.textProperty().bind(AuthState.usernameProperty());
        userStatsLabel.textProperty().bind(
            AuthState.winsProperty().asString("Wins: %d")
                .concat(" | ")
                .concat(AuthState.losesProperty().asString("Loses: %d"))
                .concat(" | ")
                .concat(AuthState.drawsProperty().asString("Draws: %d"))
        );
        userInfoBox.visibleProperty().bind(AuthState.loggedInProperty());
        userInfoBox.managedProperty().bind(userInfoBox.visibleProperty());
        userInfoBox.minWidthProperty().bind(
            Bindings.when(userInfoBox.visibleProperty()).then(Region.USE_COMPUTED_SIZE).otherwise(0.0)
        );
        if (loginButton != null) {
            loginButton.visibleProperty().bind(Bindings.not(AuthState.loggedInProperty()));
            loginButton.managedProperty().bind(loginButton.visibleProperty());
        }
        if (registerButton != null) {
            registerButton.visibleProperty().bind(Bindings.not(AuthState.loggedInProperty()));
            registerButton.managedProperty().bind(registerButton.visibleProperty());
        }
        if (logoutButton != null) {
            logoutButton.visibleProperty().bind(AuthState.loggedInProperty());
            logoutButton.managedProperty().bind(logoutButton.visibleProperty());
        }
        if (matchHistoryButton != null) {
            matchHistoryButton.visibleProperty().bind(AuthState.loggedInProperty());
            matchHistoryButton.managedProperty().bind(matchHistoryButton.visibleProperty());
        }
    }

    @FXML
    private void onSingleplayer(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
            Scene scene = new Scene(loader.load(), 900, 640);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Tanks - Duel");
            stage.setScene(scene);
        } catch (IOException ex) {
            ErrorDialog.showError("Failed to load singleplayer", ex.getMessage(), ex);
        }
    }

    @FXML
    private void onMultiplayer(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("multiplayer-view.fxml"));
            Scene scene = new Scene(loader.load(), 900, 640);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Tanks - Multiplayer");
            stage.setScene(scene);
        } catch (IOException ex) {
            ErrorDialog.showError("Failed to load multiplayer view", ex.getMessage(), ex);
        }
    }

    @FXML
    private void onMatchHistory(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("match-history-view.fxml"));
            Scene scene = new Scene(loader.load(), 900, 640);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Tanks - Match History");
            stage.setScene(scene);
        } catch (IOException ex) {
            ErrorDialog.showError("Failed to load match history", ex.getMessage(), ex);
        }
    }

    @FXML
    private void onLeaderboard(ActionEvent event) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(HelloApplication.class.getResource("leaderboard-view.fxml"));
            javafx.scene.Scene scene = new javafx.scene.Scene(loader.load(), 900, 640);
            javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Tanks - Leaderboard");
            stage.setScene(scene);
        } catch (IOException ex) {
            ErrorDialog.showError("Failed to load leaderboard", ex.getMessage(), ex);
        }
    }

    @FXML
    private void onLoginClick(ActionEvent event) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(HelloApplication.class.getResource("login-view.fxml"));
            Scene scene = new Scene(loader.load(), 450, 380);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Login");
            stage.setScene(scene);
        } catch (IOException ex) {
            ErrorDialog.showError("Failed to open login view", ex.getMessage(), ex);
        }
    }

    @FXML
    private void onRegisterClick(ActionEvent event) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(HelloApplication.class.getResource("register-view.fxml"));
            Scene scene = new Scene(loader.load(), 450, 480);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Register");
            stage.setScene(scene);
        } catch (IOException ex) {
            ErrorDialog.showError("Failed to open register view", ex.getMessage(), ex);
        }
    }

    @FXML
    private void onAuthSubmit(ActionEvent event) {
        String user = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String pass = passwordField.getText() == null ? "" : passwordField.getText();

        if (user.isEmpty() || pass.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setHeaderText("Missing credentials");
            a.setContentText("Please enter both username and password.");
            a.showAndWait();
            return;
        }

        if (authIsRegister) {
            javafx.concurrent.Task<RegistrationClient.RegistrationResult> task = new javafx.concurrent.Task<>() {
                @Override
                protected RegistrationClient.RegistrationResult call() {
                    return RegistrationClient.register(user, pass);
                }
            };

            task.setOnSucceeded(ev -> {
                RegistrationClient.RegistrationResult res = task.getValue();
                Alert a;
                if (res.isSuccess()) {
                    a = new Alert(Alert.AlertType.INFORMATION);
                    a.setHeaderText("Rejestracja powiodła się");
                    a.setContentText(res.getMessage());
                } else {
                    a = new Alert(Alert.AlertType.ERROR);
                    a.setHeaderText("Rejestracja nieudana");
                    a.setContentText(res.getMessage());
                }
                a.showAndWait();

                if (res.isSuccess()) {
                    authBox.setVisible(false);
                    usernameField.clear();
                    passwordField.clear();
                }
            });

            task.setOnFailed(ev -> {
                Throwable ex = task.getException();
                ErrorDialog.showError("Błąd rejestracji", ex == null ? "Nieznany błąd" : ex.getMessage(), ex);
            });

            new Thread(task).start();
        } else {
            javafx.concurrent.Task<AuthClient.LoginResult> task = new javafx.concurrent.Task<>() {
                @Override
                protected AuthClient.LoginResult call() {
                    return AuthClient.login(user, pass);
                }
            };

            task.setOnSucceeded(ev -> {
                AuthClient.LoginResult lr = task.getValue();
                if (lr == null || !lr.isSuccess()) {
                    Alert a = new Alert(Alert.AlertType.ERROR);
                    a.setHeaderText("Login failed");
                    a.setContentText(lr == null ? "Unknown error" : lr.getMessage());
                    a.showAndWait();
                    return;
                }

                AuthState.setToken(lr.getToken());
                AuthState.setProfile(lr.getProfile());

                Alert a = new Alert(Alert.AlertType.INFORMATION);
                a.setHeaderText("Login successful");
                a.setContentText("Welcome, " + (AuthState.getUsername() == null || AuthState.getUsername().isBlank() ? user : AuthState.getUsername()));
                a.showAndWait();

                authBox.setVisible(false);
                usernameField.clear();
                passwordField.clear();
            });

            task.setOnFailed(ev -> {
                Throwable ex = task.getException();
                ErrorDialog.showError("Login failed", ex == null ? "Unknown error" : ex.getMessage(), ex);
            });

            new Thread(task).start();
        }
    }

    @FXML
    private void onAuthCancel(ActionEvent event) {
        authBox.setVisible(false);
        usernameField.clear();
        passwordField.clear();
    }

    @FXML
    private void onExit(ActionEvent event) {
        Platform.exit();
    }

    @FXML
    private void onLogoutClick(ActionEvent event) {
        AuthState.clear();
    }
}
