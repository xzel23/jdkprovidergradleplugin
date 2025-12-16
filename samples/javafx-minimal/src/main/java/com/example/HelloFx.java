package com.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/**
 * Minimal JavaFX application used to exercise the JavaToolchainResolver plugin.
 * The plugin will discover or provision a JavaFX-bundled JDK automatically for the requested toolchain.
 */
public class HelloFx extends Application {
    @Override
    public void start(Stage stage) {
        stage.setTitle("Hello JavaFX");
        stage.setScene(new Scene(new Label("Hello, JavaFX!"), 300, 120));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
