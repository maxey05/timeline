package com.emgi.timeline;

import com.emgi.timeline.controller.IdeaListController;
import com.emgi.timeline.domain.validation.IdeaValidator;
import com.emgi.timeline.repository.IdeaRepository;
import com.emgi.timeline.repository.StorageException;
import com.emgi.timeline.repository.sqlite.SchemaInitializer;
import com.emgi.timeline.repository.sqlite.SqliteConnectionSource;
import com.emgi.timeline.repository.sqlite.SqliteIdeaRepository;
import com.emgi.timeline.seed.SampleDataSeeder;
import com.emgi.timeline.service.IdeaService;
import com.emgi.timeline.service.UuidIdGenerator;
import com.emgi.timeline.view.MainView;
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

/**
 * Entry point and composition root (ARCHITECTURE.md §3). This is the only class that names
 * {@code SqliteIdeaRepository}; everything else depends on the {@code IdeaRepository} interface.
 */
public class App extends Application
{
    private static final String FXML_MAIN = "/com/emgi/timeline/fxml/MainView.fxml";
    private static final String CSS_BASE = "/com/emgi/timeline/css/base.css";
    private static final String CSS_THEME = "/com/emgi/timeline/css/theme-mono.css";

    /** Set -Dtimeline.seed=false to launch against an empty database (see the empty-state check). */
    private static final String SEED_PROPERTY = "timeline.seed";

    private SqliteConnectionSource connectionSource;

    @Override
    public void start(Stage stage) throws IOException
    {
        // One clock, created once and passed everywhere (§10 decision #6). systemDefaultZone,
        // not systemUTC: the formatter renders "Aug 3" in the user's zone, and a UTC rendering
        // shows the wrong day for part of every day.
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
            // Without this the runtime has no window to close and the process hangs with no UI.
            Platform.exit();
            return;
        }

        MainView mainView = new MainView(listController, new IdeaDateFormatter(clock));

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
            // Shutting down: there is no UI left to tell, and an exception out of stop() is just
            // noise on the way out the door.
            System.err.println("Timeline: could not close the database cleanly — " + e.getMessage());
        }
    }

    /**
     * The composition root. {@code connectionSource} is assigned to the field before anything that
     * can throw, so a failure part-way through still leaves {@link #stop()} something to close.
     */
    private IdeaListController buildListController(Clock clock)
    {
        connectionSource = SqliteConnectionSource.atDefaultLocation();
        new SchemaInitializer().initialize(connectionSource.connection());

        IdeaRepository repository = new SqliteIdeaRepository(connectionSource);

        if(!"false".equalsIgnoreCase(System.getProperty(SEED_PROPERTY)))
        {
            new SampleDataSeeder(clock).seedIfEmpty(repository);
        }

        IdeaService service = new IdeaService(
            repository, new IdeaValidator(), new UuidIdGenerator(), clock);

        return new IdeaListController(service);
    }

    private void showStorageFailure(StorageException e)
    {
        Alert alert = new Alert(AlertType.ERROR);
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
