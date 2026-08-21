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

public final class IdeaEditorDialog
{
    private static final String FXML = "/com/emgi/timeline/fxml/IdeaEditorView.fxml";

    private static final double WIDTH = 560;
    private static final double HEIGHT = 680;

    private static final double MIN_WIDTH = 480;
    private static final double MIN_HEIGHT = 520;

    public record Result(Optional<Idea> saved, boolean targetMissing)
    {
        public Result
        {
            Objects.requireNonNull(saved, "saved");
        }
    }

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
            throw new UncheckedIOException("Could not load " + FXML, e);
        }

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(existing == null ? "New Idea" : "Edit Idea");

        Scene scene = new Scene(root, WIDTH, HEIGHT);

        if(owner != null && owner.getScene() != null)
        {
            scene.getStylesheets().setAll(owner.getScene().getStylesheets());
        }

        stage.setScene(scene);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
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
