package com.example.tanksclient;

import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ErrorDialog {
    private static final Logger LOGGER = Logger.getLogger(ErrorDialog.class.getName());
    public static void showError(String header, String content, Throwable ex) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(header == null ? "Error" : header);
        a.setContentText(content == null ? (ex == null ? "" : ex.getMessage()) : content);

        try {
            DialogPane dp = a.getDialogPane();
            var css = ErrorDialog.class.getResource("start.css");
            if (css != null) dp.getStylesheets().add(css.toExternalForm());
            dp.getStyleClass().add("dialog-pane");
        } catch (Exception e) { LOGGER.log(Level.FINE, "Failed to apply dialog stylesheet", e); }

        if (ex != null) {
            LOGGER.log(Level.WARNING, "An error occurred: " + ex.getMessage(), ex);
        }

        a.showAndWait();
    }

    public static void showError(String header, String content) {
        showError(header, content, null);
    }
}
