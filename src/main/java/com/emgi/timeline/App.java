package com.emgi.timeline;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public class App extends Application
{
    private static final String FXML_MAIN = "/com/emgi/timeline/fxml/MainView.fxml";
    private static final String CSS_BASE = "/com/emgi/timeline/css/base.css";
    private static final String CSS_THEME = "/com/emgi/timeline/css/theme-mono.css";

    @Override
    public void start(Stage stage) throws IOException
    {
        FXMLLoader loader = new FXMLLoader(resource(FXML_MAIN));
        Parent root = loader.load();

        Scene scene = new Scene(root, 900, 640);
        scene.getStylesheets().addAll(
            resource(CSS_BASE).toExternalForm(),
            resource(CSS_THEME).toExternalForm()
        );

        stage.setTitle("Timeline");
        stage.setMinWidth(640);
        stage.setMinHeight(480);
        stage.setScene(scene);
        stage.show();
    }

    private static URL resource(String path)
    {
        return Objects.requireNonNull(
            App.class.getResource(path), "Missing classpath resource: " + path
        );
    }
}
