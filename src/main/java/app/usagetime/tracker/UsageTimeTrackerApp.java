package app.usagetime.tracker;

import app.usagetime.tracker.platform.ForegroundProvider;
import app.usagetime.tracker.platform.PlatformProviders;
import app.usagetime.tracker.storage.SqliteUsageRepository;
import app.usagetime.tracker.storage.UsageRepository;
import app.usagetime.tracker.tracking.TrackingEngine;
import app.usagetime.tracker.ui.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

public final class UsageTimeTrackerApp extends Application {
    private UsageRepository repository;
    private TrackingEngine engine;

    @Override
    public void start(Stage stage) {
        Path databasePath = Path.of(System.getProperty("user.home"), ".usage-time-tracker", "usage.db");
        repository = new SqliteUsageRepository(databasePath);
        AtomicReference<MainView> viewReference = new AtomicReference<>();
        ForegroundProvider provider = PlatformProviders.create();
        engine = new TrackingEngine(
                provider,
                repository,
                Clock.systemDefaultZone(),
                status -> Platform.runLater(() -> {
                    MainView view = viewReference.get();
                    if (view != null) {
                        view.setTrackingStatus(status);
                    }
                }),
                () -> Platform.runLater(() -> {
                    MainView view = viewReference.get();
                    if (view != null) {
                        view.refresh();
                    }
                })
        );

        MainView view = new MainView(repository, engine, databasePath, ignored -> {
        });
        viewReference.set(view);
        Scene scene = new Scene(view.root(), 1120, 760);
        boolean dark = repository.setting("theme", "light").equals("dark");
        view.installStylesheet(scene, dark);
        stage.setTitle("UsageTime Tracker");
        stage.setMinWidth(840);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> stop());
        stage.show();
        engine.start();
    }

    @Override
    public void stop() {
        if (engine != null) {
            engine.close();
            engine = null;
        }
        if (repository != null) {
            repository.close();
            repository = null;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
