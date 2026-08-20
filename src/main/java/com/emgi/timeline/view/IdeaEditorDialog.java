package com.emgi.timeline.view;

import com.emgi.timeline.controller.IdeaEditorController;
import com.emgi.timeline.domain.model.Idea;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Opens the editor as a modal dialog (locked decision #5) and reports what came back.
 *
 * <p>A plain {@link Stage}, not a {@code Dialog}/{@code DialogPane}: a {@code DialogPane}'s buttons
 * close the dialog on click, so holding an invalid form open would need an event filter on a
 * looked-up button, and its default styling fights §6.2's palette. Two ordinary buttons in a
 * {@code Stage} have neither problem.
 */
public final class IdeaEditorDialog
{
    private static final String FXML = "/com/emgi/timeline/fxml/IdeaEditorView.fxml";

    private static final double WIDTH = 520;
    private static final double HEIGHT = 560;

    /**
     * What one dialog session produced.
     *
     * @param saved         the persisted idea, empty if the user cancelled or the save failed
     * @param targetMissing the idea being edited had been deleted — the caller's list is stale
     */
    public record Result(Optional<Idea> saved, boolean targetMissing)
    {
        public Result
        {
            Objects.requireNonNull(saved, "saved");
        }
    }

    /** One fresh controller per dialog — they are not reusable, and are not meant to be. */
    private final Supplier<IdeaEditorController> controllers;

    public IdeaEditorDialog(Supplier<IdeaEditorController> controllers)
    {
        this.controllers = Objects.requireNonNull(controllers, "controllers");
    }

    public Result showCreate(Window owner)
    {
        return show(owner, null);
    }

    public Result showEdit(Window owner, Idea idea)
    {
        return show(owner, Objects.requireNonNull(idea, "idea"));
    }

    private Result show(Window owner, Idea existing)
    {
        IdeaEditorController controller = controllers.get();

        // Populate before the FXML loads: initialize() reads the form model to pick the selected
        // status radio, so an empty controller here would always show "Incomplete".
        if(existing == null)
        {
            controller.beginCreate();
        }
        else
        {
            controller.beginEdit(existing);
        }

        IdeaEditorView view = new IdeaEditorView(controller);

        FXMLLoader loader = new FXMLLoader(resource());
        loader.setControllerFactory(type ->
        {
            if(type == IdeaEditorView.class)
            {
                return view;
            }

            throw new IllegalStateException("No controller factory registered for " + type);
        });

        Parent root;
        try
        {
            root = loader.load();
        }
        catch(IOException e)
        {
            // A missing or malformed FXML on the classpath is a packaging bug, not a user error.
            throw new UncheckedIOException("Could not load " + FXML, e);
        }

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(existing == null ? "New Idea" : "Edit Idea");

        Scene scene = new Scene(root, WIDTH, HEIGHT);

        // Inherit the main window's stylesheets rather than re-listing the paths — one place to
        // change when the theme file is swapped (§10, "theming is a file swap").
        if(owner != null && owner.getScene() != null)
        {
            scene.getStylesheets().setAll(owner.getScene().getStylesheets());
        }

        stage.setScene(scene);
        view.setStage(stage);
        stage.showAndWait();

        return new Result(controller.savedIdea(), controller.targetMissing());
    }

    private static URL resource()
    {
        return Objects.requireNonNull(
            IdeaEditorDialog.class.getResource(FXML), "Missing classpath resource: " + FXML
        );
    }
}
