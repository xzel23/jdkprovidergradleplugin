// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 Axel Howind
// This file is part of the JDK Provider Gradle Plugin.
// The JDK Provider Gradle Plugin is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// The JDK Provider Gradle Plugin is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
// You should have received a copy of the GNU General Public License
// along with this program. If not, see https://www.gnu.org/licenses/

package com.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/**
 * Minimal JavaFX application used to exercise the JavaToolchainResolver plugin.
 * The plugin will discover or provision a JavaFX-bundled JDK automatically for the requested toolchain.
 */
public class HelloFxJLink extends Application {
    @Override
    public void start(Stage stage) {
        stage.setTitle("Hello JavaFX (JLink version)");
        stage.setScene(new Scene(new Label("Hello, JavaFX!"), 300, 120));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
