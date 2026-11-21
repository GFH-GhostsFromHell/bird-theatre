package ca.gfh.birdtheatre;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import java.net.URI;

public class BirdTheatreDesktop extends Application {
    private final String origin = System.getProperty("birdtheatre.url", "https://bird-theatre.onrender.com");
    private final UpdateService updater = new UpdateService();

    @Override public void start(Stage stage) {
        DesktopDiagnostics.event("JavaFX start entered");
        javafx.scene.layout.StackPane viewer = new javafx.scene.layout.StackPane();
        showMessage(viewer, "Opening Bird Theatre", "Connecting to the community portal…", "explore");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 18, 12, 18));
        header.setStyle("-fx-background-color: #102c25;");
        Label brand = new Label("✦  bird theatre.");
        brand.setStyle("-fx-text-fill: white; -fx-font-size: 21px; -fx-font-weight: bold;");
        header.getChildren().add(brand);
        for (String[] page : new String[][]{
                {"explore", "Explore"}, {"cameras", "My stations"},
                {"sightings", "Visits"}, {"alerts", "Alerts"}, {"rfid", "RFID"}}) {
            Button button = new Button(page[1]);
            button.setStyle("-fx-background-color: #dae9d8; -fx-background-radius: 15; -fx-font-size: 12px;");
            button.setOnAction(event -> loadPortal(viewer, page[0]));
            header.getChildren().add(button);
        }
        Button openBrowser = new Button("Open browser ↗");
        openBrowser.setOnAction(event -> {
            try { java.awt.Desktop.getDesktop().browse(URI.create(origin)); }
            catch (Exception ignored) { }
        });
        header.getChildren().add(openBrowser);

        Label updateStatus = new Label("Checking update configuration…");
        updateStatus.setStyle("-fx-text-fill: #214c3f; -fx-font-size: 12px;");
        Button checkUpdates = new Button("Check for updates");
        checkUpdates.setOnAction(e -> updater.checkNow());
        Button installNow = new Button("Install update now");
        installNow.setVisible(false);
        installNow.managedProperty().bind(installNow.visibleProperty());
        installNow.setOnAction(event -> {
            if (updater.isStaged()) Platform.exit();
        });
        HBox footer = new HBox(12, updateStatus, checkUpdates, installNow);
        footer.setPadding(new Insets(9, 16, 9, 16));
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-background-color: #e8f1e6;");

        BorderPane root = new BorderPane(viewer);
        root.setTop(header);
        root.setBottom(footer);
        Scene scene = new Scene(root, 1150, 760, Color.WHITE);
        stage.setTitle("Bird Theatre — Wildlife community · v" + updater.currentVersion());
        stage.setScene(scene);
        stage.show();
        DesktopDiagnostics.event("JavaFX window displayed successfully");
        loadPortal(viewer, "explore");

        updater.start(status -> Platform.runLater(() -> updateStatus.setText(status)),
            () -> Platform.runLater(() -> installNow.setVisible(true)));
    }

    /** A failed server connection does not prevent the native desktop window from opening. */
    private void loadPortal(javafx.scene.layout.StackPane container, String section) {
        showMessage(container, "Connecting to Bird Theatre", "Checking the community web service…", section);
        URI address;
        try {
            address = URI.create(origin);
            if (!"https".equalsIgnoreCase(address.getScheme())
                || address.getHost() == null || address.getRawUserInfo() != null)
                throw new IllegalArgumentException("A secure HTTPS portal URL is required");
        } catch (Exception invalid) {
            DesktopDiagnostics.report("portal configuration", invalid);
            showMessage(container, "Portal URL needs attention",
                "The desktop app is installed, but its portal address is invalid.", section);
            return;
        }
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(address)
            .timeout(java.time.Duration.ofSeconds(8)).GET().build();
        java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build()
            .sendAsync(request, java.net.http.HttpResponse.BodyHandlers.discarding())
            .whenComplete((response, error) -> Platform.runLater(() -> {
                if (error != null || response == null || response.statusCode() >= 400) {
                    String reason = error != null ? "Cloud service could not be reached"
                        : "Server returned HTTP " + response.statusCode();
                    DesktopDiagnostics.event("Portal unavailable: " + reason);
                    showMessage(container, "Bird Theatre desktop is installed",
                        reason + ". The web portal may not be deployed yet. "
                        + "Retry when the community service is ready.", section);
                    return;
                }
                try {
                    WebView browser = new WebView();
                    browser.getEngine().getLoadWorker().stateProperty().addListener((o, a, state) -> {
                        if (state == javafx.concurrent.Worker.State.FAILED) {
                            Throwable failure = browser.getEngine().getLoadWorker().getException();
                            if (failure != null) DesktopDiagnostics.report("web page", failure);
                            showMessage(container, "Unable to load the page",
                                "Please check your connection and retry.", section);
                        }
                    });
                    container.getChildren().setAll(browser);
                    browser.getEngine().load(origin.replaceAll("/+$", "") + "/#" + section);
                    DesktopDiagnostics.event("Web view created: " + section);
                } catch (Throwable failure) {
                    DesktopDiagnostics.report("web view", failure);
                    showMessage(container, "Built-in browser could not start",
                        "See the diagnostic log at " + DesktopDiagnostics.logPath(), section);
                }
            }));
    }

    private void showMessage(javafx.scene.layout.StackPane container,
                             String heading, String detail, String section) {
        Label title = new Label(heading);
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #123b2c");
        title.setWrapText(true);
        Label body = new Label(detail);
        body.setStyle("-fx-font-size: 15px; -fx-text-fill: #38554a");
        body.setWrapText(true);
        body.setMaxWidth(620);
        Button retry = new Button("Retry connection");
        retry.setOnAction(e -> loadPortal(container, section));
        javafx.scene.layout.VBox area = new javafx.scene.layout.VBox(16, title, body, retry);
        area.setAlignment(Pos.CENTER);
        area.setPadding(new Insets(32));
        area.setStyle("-fx-background-color: #f3f8f0");
        container.getChildren().setAll(area);
    }

    @Override public void stop() {
        updater.stop();
        updater.installStagedAfterExit();
        DesktopDiagnostics.event("Desktop exited normally");
    }

    public static void main(String[] args) { launch(args); }
}
