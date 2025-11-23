package com.example.tanksclient;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label statusLabel;

    @FXML
    private void onLogin() {
        String user = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String pass = passwordField.getText() == null ? "" : passwordField.getText();
        if (user.isEmpty() || pass.isEmpty()) {
            setError("Enter username and password");
            return;
        }

        javafx.concurrent.Task<AuthClient.LoginResult> task = new javafx.concurrent.Task<>() {
            @Override
            protected AuthClient.LoginResult call() {
                return AuthClient.login(user, pass);
            }
        };

        task.setOnSucceeded(ev -> {
            AuthClient.LoginResult lr = task.getValue();
            if (lr != null && lr.isSuccess()) {
                AuthState.setToken(lr.getToken());
                AuthState.setProfile(lr.getProfile());
                try {
                    Navigation.goToStart(usernameField);
                } catch (Exception ex) {
                    ErrorDialog.showError("Navigation error", "Failed to navigate to the main menu", ex);
                }
                } else {
                    String msg = lr == null ? "Login failed" : lr.getMessage();
                    setError(msg == null ? "Login failed" : msg);
                }
        });

        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            setError(ex == null ? "Error" : ex.getMessage());
        });

        new Thread(task).start();
    }

    @FXML
    private void onCancel() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(HelloApplication.class.getResource("start-view.fxml"));
            javafx.scene.Parent root = loader.load();
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new javafx.scene.Scene(root, 900, 640));
            stage.setTitle("Tanks - Duel");
        } catch (Exception ex) {
            ErrorDialog.showError("Navigation error", "Failed to navigate to the main menu", ex);
        }
    }

    private void setError(String msg) {
        if (statusLabel != null) {
            statusLabel.setStyle("-fx-text-fill: red;");
            statusLabel.setText(msg);
        }
    }

    private void setSuccess(String msg) {
        if (statusLabel != null) {
            statusLabel.setStyle("-fx-text-fill: green;");
            statusLabel.setText(msg);
        }
    }
}
