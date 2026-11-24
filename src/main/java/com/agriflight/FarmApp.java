package com.agriflight;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * The JavaFX entry point. JavaFX creates the {@link Application} object itself, through a public
 * constructor, so the Singleton can't be the Application class. This launcher only asks the
 * singleton {@link FarmDashboard} to open its one window.
 */
public final class FarmApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        FarmDashboard.getInstance().open();
    }
}
