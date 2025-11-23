module com.example.tanksclient {
    requires javafx.controls;
    requires javafx.fxml;

    // Lombok is only needed at compile-time for annotation processing
    requires static lombok;

    // HTTP client
    requires java.net.http;

    // Logging
    requires java.logging;

    // Jackson (JSON)
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.annotation;


    opens com.example.tanksclient to javafx.fxml;
    exports com.example.tanksclient;
}