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
        if (!URI.create(origin).getScheme().equals("https"))
            throw new IllegalArgumentException("Portal URL must be HTTPS");
        WebView browser = new WebView();
        browser.getEngine().load(origin + "/#explore");

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
            button.setOnAction(event -> browser.getEngine().load(origin + "/#" + page[0]));
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

        BorderPane root = new BorderPane(browser);
        root.setTop(header);
        root.setBottom(footer);
        Scene scene = new Scene(root, 1150, 760, Color.WHITE);
        stage.setTitle("Bird Theatre — Wildlife community · v" + updater.currentVersion());
        stage.setScene(scene);
        stage.show();

        updater.start(status -> Platform.runLater(() -> updateStatus.setText(status)),
            () -> Platform.runLater(() -> installNow.setVisible(true)));
    }

    @Override public void stop() {
        updater.stop();
        updater.installStagedAfterExit();
    }

    public static void main(String[] args) { launch(args); }
}
