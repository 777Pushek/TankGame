package com.example.tanksclient;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class Navigation {
    private Navigation() {}

    public static FXMLLoader loadAndSetScene(Node sourceNode, String fxmlResource, double width, double height, String title) throws Exception {
        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource(fxmlResource));
        Parent root = loader.load();
        Stage stage = (Stage) sourceNode.getScene().getWindow();
        stage.setScene(new Scene(root, width, height));
        stage.setTitle(title);
        return loader;
    }

    public static void goToStart(Node sourceNode) throws Exception {
        loadAndSetScene(sourceNode, "start-view.fxml", 900, 640, "Tanks - Duel");
    }
}
