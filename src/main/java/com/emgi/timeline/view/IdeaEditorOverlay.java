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
