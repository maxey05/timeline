package com.emgi.timeline.view;

import com.emgi.timeline.controller.IdeaEditorController;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.service.ImageStore;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Builds the idea editor as a node to lay inside the main window, rather than as its own
 * Stage. Nothing here shows anything: MainView owns the overlay the node goes into, which is
 * what lets the editor be modal to the window without a second Stage of its own.
 *
 * <p>The consequence worth knowing: this replaced a {@code showAndWait()} that blocked until
 * the user was done, so there is no return value to read on the next line any more. A caller
 * opens a {@link Session}, hands the view a way to close, and reads {@link Session#result()}
 * from that callback.
 */
public final class IdeaEditorOverlay
{
    private static final String FXML = "/com/emgi/timeline/fxml/IdeaEditorView.fxml";

    public record Result(Optional<Idea> saved, boolean targetMissing)
    {
        public Result
        {
            Objects.requireNonNull(saved, "saved");
        }
    }

    /** One open editor: the node on screen, the view driving it, and its outcome so far. */
    public static final class Session
    {
        private final Parent root;

        private final IdeaEditorView view;

        private final IdeaEditorController controller;

        private final boolean creating;

        private Session(Parent root, IdeaEditorView view,
                        IdeaEditorController controller, boolean creating)
        {
            this.root = root;
            this.view = view;
            this.controller = controller;
            this.creating = creating;
        }

        public Parent root()
        {
            return root;
        }

        public IdeaEditorView view()
        {
            return view;
        }

        /** Create and edit finish differently -- one adds to the list, the other replaces. */
        public boolean creating()
        {
            return creating;
        }

        public Result result()
        {
            return new Result(controller.savedIdea(), controller.targetMissing());
        }
    }

    private final Supplier<IdeaEditorController> controllers;

    private final ImageStore imageStore;

    public IdeaEditorOverlay(Supplier<IdeaEditorController> controllers, ImageStore imageStore)
    {
        this.controllers = Objects.requireNonNull(controllers, "controllers");
        this.imageStore = Objects.requireNonNull(imageStore, "imageStore");
    }

    public Session createSession()
    {
        return build(null);
    }

    public Session editSession(Idea idea)
    {
        return build(Objects.requireNonNull(idea, "idea"));
    }

    /*
     * A fresh controller and a fresh load of the FXML per session. Reusing one editor node
     * would mean resetting the form model by hand on every open, and its default and cancel
     * buttons are scene-wide -- they would keep firing on Enter and Esc long after the editor
     * was hidden.
     */
    private Session build(Idea existing)
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

        IdeaEditorView view = new IdeaEditorView(controller, imageStore);

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

        return new Session(root, view, controller, existing == null);
    }

    private static URL resource()
    {
        return Objects.requireNonNull(
            IdeaEditorOverlay.class.getResource(FXML), "Missing classpath resource: " + FXML
        );
    }
}
