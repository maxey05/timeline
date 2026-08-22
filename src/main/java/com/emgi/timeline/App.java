package com.emgi.timeline;

import com.emgi.timeline.controller.IdeaEditorController;
import com.emgi.timeline.controller.IdeaListController;
import com.emgi.timeline.domain.validation.IdeaValidator;
import com.emgi.timeline.repository.IdeaRepository;
import com.emgi.timeline.repository.StorageException;
import com.emgi.timeline.repository.sqlite.SchemaInitializer;
import com.emgi.timeline.repository.sqlite.SqliteConnectionSource;
import com.emgi.timeline.repository.sqlite.SqliteIdeaRepository;
import com.emgi.timeline.service.IdeaService;
import com.emgi.timeline.service.ImageStore;
import com.emgi.timeline.service.UuidIdGenerator;
import com.emgi.timeline.settings.PreferencesDisplayNameStore;
import com.emgi.timeline.settings.PreferencesWindowStateStore;
import com.emgi.timeline.view.IdeaEditorOverlay;
import com.emgi.timeline.view.MainView;
import com.emgi.timeline.view.Theme;
import com.emgi.timeline.view.WindowGeometry;
import com.emgi.timeline.view.content.DescriptionRenderer;
import com.emgi.timeline.view.format.IdeaDateFormatter;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.time.Clock;
import java.util.Objects;

public class App extends Application
{
    private static final String FXML_MAIN = "/com/emgi/timeline/fxml/MainView.fxml";

    private SqliteConnectionSource connectionSource;

    private IdeaService service;

    @Override
    public void start(Stage stage) throws IOException
    {
        Theme.loadFonts();

        Clock clock = Clock.systemDefaultZone();

        IdeaListController listController;
        try
        {
            listController = buildListController(clock);
            listController.load();
        }
        catch(StorageException e)
        {
            showStorageFailure(e);
            Platform.exit();
            return;
        }

        IdeaEditorOverlay editors = new IdeaEditorOverlay(
            () -> new IdeaEditorController(service), ImageStore.atDefaultLocation());

        DescriptionRenderer descriptionRenderer =
            new DescriptionRenderer(uri -> getHostServices().showDocument(uri.toString()));

        MainView mainView = new MainView(
            listController, new IdeaDateFormatter(clock), editors, descriptionRenderer,
            PreferencesDisplayNameStore.atUserNode());

        FXMLLoader loader = new FXMLLoader(resource(FXML_MAIN));
        loader.setControllerFactory(type ->
        {
            if(type == MainView.class)
            {
                return mainView;
            }

            throw new IllegalStateException("No controller factory registered for " + type);
        });

        Parent root = loader.load();

        Scene scene = new Scene(root, 900, 640);
        Theme.applyTo(scene);

        stage.setTitle("Timeline");
        stage.setMinWidth(820);
        stage.setMinHeight(560);
        stage.setScene(scene);

        new WindowGeometry(PreferencesWindowStateStore.atUserNode()).install(stage);

        stage.show();
    }

    @Override
    public void stop()
    {
        if(connectionSource == null)
        {
            return;
        }

        try
        {
            connectionSource.close();
        }
        catch(StorageException e)
        {
            System.err.println("Timeline: could not close the database cleanly — " + e.getMessage());
        }
    }

    private IdeaListController buildListController(Clock clock)
    {
        connectionSource = SqliteConnectionSource.atDefaultLocation();
        new SchemaInitializer().initialize(connectionSource.connection());

        IdeaRepository repository = new SqliteIdeaRepository(connectionSource);

        this.service = new IdeaService(
            repository, new IdeaValidator(), new UuidIdGenerator(), clock);

        return new IdeaListController(service);
    }

    private void showStorageFailure(StorageException e)
    {
        Alert alert = new Alert(AlertType.ERROR);
        Theme.applyTo(alert);
        alert.setTitle("Timeline");
        alert.setHeaderText("Timeline can't open its database.");
        alert.setContentText(
            "The database file is at:\n"
            + SqliteConnectionSource.defaultDatabaseFile()
            + "\n\nIt may be open in another program, or damaged.\n\nDetails: "
            + e.getMessage());
        alert.showAndWait();
    }

    private static URL resource(String path)
    {
        return Objects.requireNonNull(
            App.class.getResource(path), "Missing classpath resource: " + path
        );
    }
}
