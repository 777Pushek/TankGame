package com.example.tanksclient;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class RegisterController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label statusLabel;

    @FXML
    private void onRegister() {
        String user = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String pass = passwordField.getText() == null ? "" : passwordField.getText();
        String confirm = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();
        if (user.isEmpty() || pass.isEmpty()) {
            setError("Enter username and password");
            return;
        }

        if (!user.matches("^[A-Za-z0-9_]{3,20}$")) {
            setError("Username must be 3-20 characters and contain only letters, digits, and underscores");
            return;
        }
        if (pass.length() < 8) {
            setError("Password must be at least 8 characters");
            return;
        }
        if (!pass.matches(".*[A-Z].*")) {
            setError("Password must contain at least one uppercase letter");
            return;
        }
        if (!pass.matches(".*[a-z].*")) {
            setError("Password must contain at least one lowercase letter");
            return;
        }
        if (!pass.matches(".*\\d.*")) {
            setError("Password must contain at least one digit");
            return;
        }
        if (!pass.equals(confirm)) {
            setError("Passwords do not match");
            return;
        }

        javafx.concurrent.Task<RegistrationClient.RegistrationResult> task = new javafx.concurrent.Task<>() {
            @Override
            protected RegistrationClient.RegistrationResult call() {
                return RegistrationClient.register(user, pass);
            }
        };

        task.setOnSucceeded(ev -> {
            RegistrationClient.RegistrationResult res = task.getValue();
                if (res != null && res.isSuccess()) {
                try {
                        setSuccess(res.getMessage() == null ? "Registered" : res.getMessage());
                        Thread.sleep(400);
                    Navigation.goToStart(usernameField);
                } catch (Exception ex) {
                    ErrorDialog.showError("Navigation error", "Failed to navigate to the main menu", ex);
                }
            } else {
                setError(res == null ? "Registration failed" : res.getMessage());
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
