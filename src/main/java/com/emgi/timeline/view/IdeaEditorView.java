package com.emgi.timeline.view;

import com.emgi.timeline.controller.IdeaEditorController;
import com.emgi.timeline.controller.IdeaEditorController.SaveResult;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.service.ImageStore;
import com.emgi.timeline.view.content.DescriptionArea;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.reactfx.Subscription;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class IdeaEditorView
{
    private static final String ERROR_PREFIX = "⚠ ";

    private static final ButtonType DISCARD =
            new ButtonType("Discard", ButtonBar.ButtonData.OK_DONE);

    private static final ButtonType KEEP_EDITING =
            new ButtonType("Keep editing", ButtonBar.ButtonData.CANCEL_CLOSE);

    private static final List<String> IMAGE_EXTENSIONS =
            List.of("png", "jpg", "jpeg", "gif", "bmp");

    private final IdeaEditorController controller;

    private final ImageStore imageStore;

    /*
     * What the Stage used to be. An owner to parent alerts on, the scene carrying this
     * editor's shortcut filter, and a way to close -- MainView hides the overlay.
     */
    private Window owner;

    private Scene scene;

    private Runnable closeAction;

    /*
     * Held rather than written as this::onEditorKey at both call sites: a method reference
     * is a fresh object each time it is evaluated, so removeEventFilter would silently
     * remove nothing and every open would leave another filter on the scene.
     */
    private final EventHandler<KeyEvent> keyFilter = this::onEditorKey;

    @FXML
    private TextField titleField;

    @FXML
    private Label titleErrorLabel;

    @FXML
    private FlowPane tagPane;

    @FXML
    private TextField tagField;

    @FXML
    private Label tagsErrorLabel;

    @FXML
    private HBox statusBox;

    @FXML
    private StackPane descriptionStack;

    /*
     * NOT @FXML. A GenericStyledArea is generic in three type parameters and has no no-arg
     * constructor FXML could call, so the writing surface is built in code and dropped into
     * the descriptionStack placeholder that IS injected.
     */
    private DescriptionArea descriptionArea;

    /** Pushes edits into the controller. Held so detach can unsubscribe it. */
    private Subscription descriptionChanges;

    /** True while load() is rewriting the document, so its own edits are not pushed back. */
    private boolean loadingDescription;

    @FXML
    private Button insertImageButton;

    @FXML
    private Label descriptionErrorLabel;

    @FXML
    private Button saveButton;

    @FXML
    private Button cancelButton;

    public IdeaEditorView(IdeaEditorController controller, ImageStore imageStore)
    {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.imageStore = Objects.requireNonNull(imageStore, "imageStore");
    }

    /**
     * Hands the view what the Stage used to give it. There is no onCloseRequest hook on an
     * overlay, so the Esc route in is {@link #requestClose()}, which MainView calls from its
     * own scene filter.
     */
    void attach(Window owner, Scene scene, Runnable closeAction)
    {
        this.owner = owner;
        this.scene = Objects.requireNonNull(scene, "scene");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");

        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyFilter);

        Platform.runLater(titleField::requestFocus);
    }

    /** Takes the shortcut filter back off the shared scene. Closing without this leaks it. */
    void detach()
    {
        if(scene != null)
        {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
        }

        /*
         * Both are required. The subscription would keep pushing edits into a controller
         * whose editor is gone, and GenericStyledArea holds internal subscriptions of its
         * own that only dispose() releases -- a TextArea never needed either.
         */
        if(descriptionChanges != null)
        {
            descriptionChanges.unsubscribe();
            descriptionChanges = null;
        }

        if(descriptionArea != null)
        {
            descriptionArea.dispose();
        }

        scene = null;
        closeAction = null;
    }

    /** Esc or Cancel: confirm before throwing away anything the user typed. */
    void requestClose()
    {
        if(confirmDiscard())
        {
            close();
        }
    }

    /**
     * Tab cycles within these while the editor is open. The tag chips' remove buttons are
     * deliberately out of the ring: they appear and disappear as tags are added, and a ring
     * that changes shape under the user is worse than one that needs a click.
     */
    List<Node> focusRing()
    {
        List<Node> ring = new ArrayList<>();

        ring.add(titleField);
        ring.add(tagField);
        ring.addAll(statusBox.getChildren());
        ring.add(descriptionArea);
        ring.add(insertImageButton);
        ring.add(cancelButton);
        ring.add(saveButton);

        return List.copyOf(ring);
    }

    private void close()
    {
        if(closeAction != null)
        {
            closeAction.run();
        }
    }

    @FXML
    private void initialize()
    {
        if(titleField == null || tagPane == null || statusBox == null || descriptionStack == null
            || insertImageButton == null || saveButton == null
            || cancelButton == null)
        {
            throw new IllegalStateException(
                "FXML injection failed, check fx:id and the fx:controller class name."
            );
        }

        titleField.getStyleClass().add("title-input");
        titleField.textProperty().bindBidirectional(controller.titleProperty());

        buildDescriptionArea();

        insertImageButton.setOnAction(event -> chooseImages());
        insertImageButton.setTooltip(new Tooltip("Insert an image…  (Ctrl+I)"));
        insertImageButton.setAccessibleText("Insert an image");

        bindError(titleErrorLabel, controller.titleErrorProperty());
        bindError(tagsErrorLabel, controller.tagsErrorProperty());
        bindError(descriptionErrorLabel, controller.descriptionErrorProperty());

        buildStatusControls();

        renderTags();
        controller.tags().addListener((ListChangeListener<Tag>) change -> renderTags());

        tagField.setOnAction(event ->
        {
            if(controller.addTag(tagField.getText()))
            {
                tagField.clear();
            }
        });

        saveButton.setOnAction(event -> onSave());
        cancelButton.setOnAction(event -> requestClose());

        saveButton.setTooltip(new Tooltip("Save  (Ctrl+Enter)"));
        cancelButton.setTooltip(new Tooltip("Cancel  (Esc)"));
    }

    /**
     * Builds the writing surface and keeps it and the controller in step.
     *
     * <p>There is no bindBidirectional here, and there cannot be: the document is not a
     * String property, so the two directions are different operations. Loading parses a
     * string into a document; editing serialises a document back into a string. The guard
     * flag is what stops the first from triggering the second.
     *
     * <p>The area goes inside a VirtualizedScrollPane rather than scrolling itself. That is
     * how RichTextFX scrolls -- the area renders only the paragraphs actually on screen, and
     * the scroll pane is the thing that knows which those are.
     */
    private void buildDescriptionArea()
    {
        descriptionArea = new DescriptionArea();

        VirtualizedScrollPane<DescriptionArea> scroll =
            new VirtualizedScrollPane<>(descriptionArea);
        scroll.getStyleClass().add("description-scroll");

        // Index 0: the insert-image button is already in the stack and must stay on top.
        descriptionStack.getChildren().add(0, scroll);

        loadingDescription = true;
        descriptionArea.load(controller.descriptionProperty().get());
        loadingDescription = false;

        descriptionChanges = descriptionArea.plainTextChanges().subscribe(change ->
        {
            if(!loadingDescription)
            {
                controller.descriptionProperty().set(descriptionArea.describedText());
            }
        });
    }

    private boolean confirmDiscard()
    {
        if(!controller.isDirty())
        {
            return true;
        }

        Alert confirm = new Alert(AlertType.CONFIRMATION);
        Theme.applyTo(confirm);
        confirm.initOwner(owner);
        confirm.setTitle("Timeline");
        confirm.setHeaderText("Discard your changes?");
        confirm.setContentText(controller.isEditing()
            ? "Your edits to this idea haven't been saved."
            : "This idea hasn't been saved.");
        confirm.getButtonTypes().setAll(KEEP_EDITING, DISCARD);

        Button keep = (Button) confirm.getDialogPane().lookupButton(KEEP_EDITING);
        keep.setDefaultButton(true);
        Button discard = (Button) confirm.getDialogPane().lookupButton(DISCARD);
        discard.setDefaultButton(false);

        return confirm.showAndWait().filter(choice -> choice == DISCARD).isPresent();
    }

    /*
     * A Scene filter, not a handler: the writing surface consumes most keys while they
     * bubble, and accelerators only fire on unconsumed events. Filters run top-down, so this
     * sees the keystroke first. That is also why Ctrl+V is intercepted here rather than on
     * the area -- and why it must NOT consume when the clipboard holds no picture, so the
     * area's own paste still runs and a copied link lands as text.
     */
    private void onEditorKey(KeyEvent event)
    {
        if(!event.isShortcutDown())
        {
            return;
        }

        switch(event.getCode())
        {
            case ENTER ->
            {
                onSave();
                event.consume();
            }
            case I ->
            {
                chooseImages();
                event.consume();
            }
            case V ->
            {
                if(isDescriptionFocused() && pasteImages())
                {
                    event.consume();
                }
            }
            default ->
            {
            }
        }
    }

    private boolean isDescriptionFocused()
    {
        return descriptionArea != null && descriptionArea.isFocused();
    }

    /** Copies any picture on the clipboard into the store. False means "not a picture". */
    private boolean pasteImages()
    {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        List<URI> stored = new ArrayList<>();

        try
        {
            if(clipboard.hasFiles())
            {
                for(File file : clipboard.getFiles())
                {
                    if(isImageFile(file))
                    {
                        stored.add(imageStore.copyFrom(file.toPath()));
                    }
                }
            }

            if(stored.isEmpty() && clipboard.hasImage())
            {
                byte[] png = toPngBytes(clipboard.getImage());

                if(png != null)
                {
                    stored.add(imageStore.store(png, ImageStore.DEFAULT_EXTENSION));
                }
            }
        }
        catch(UncheckedIOException e)
        {
            showImageFailure(e);
            return true;
        }

        if(stored.isEmpty())
        {
            return false;
        }

        insertImages(stored);
        return true;
    }

    private void chooseImages()
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Insert an image");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));

        List<File> chosen = chooser.showOpenMultipleDialog(owner);

        if(chosen == null || chosen.isEmpty())
        {
            return;
        }

        List<URI> stored = new ArrayList<>(chosen.size());

        try
        {
            for(File file : chosen)
            {
                stored.add(imageStore.copyFrom(file.toPath()));
            }
        }
        catch(UncheckedIOException e)
        {
            showImageFailure(e);
            return;
        }

        insertImages(stored);
        descriptionArea.requestFocus();
    }

    /*
     * All of the caret and newline bookkeeping this used to do moved into DescriptionArea,
     * where the document actually lives. What is left is the part that belongs to the view:
     * put the caret back where the user was typing.
     */
    private void insertImages(List<URI> sources)
    {
        descriptionArea.insertImages(sources);
    }

    private static boolean isImageFile(File file)
    {
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');

        return dot >= 0 && IMAGE_EXTENSIONS.contains(name.substring(dot + 1));
    }

    private static byte[] toPngBytes(Image image)
    {
        if(image == null)
        {
            return null;
        }

        BufferedImage buffered = SwingFXUtils.fromFXImage(image, null);

        if(buffered == null)
        {
            return null;
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try
        {
            if(!ImageIO.write(buffered, "png", bytes))
            {
                return null;
            }
        }
        catch(IOException e)
        {
            throw new UncheckedIOException("Could not encode the pasted image as PNG", e);
        }

        return bytes.toByteArray();
    }

    private void showImageFailure(UncheckedIOException e)
    {
        Alert alert = new Alert(AlertType.ERROR);
        Theme.applyTo(alert);
        alert.initOwner(owner);
        alert.setTitle("Timeline");
        alert.setHeaderText("Timeline couldn't save that image.");
        alert.setContentText("Images are copied into:\n"
            + imageStore.directory()
            + "\n\nDetails: " + e.getMessage());
        alert.showAndWait();
    }

    private static void bindError(Label label, ObservableValue<String> message)
    {
        label.textProperty().bind(Bindings.createStringBinding(
            () -> isBlank(message.getValue()) ? "" : ERROR_PREFIX + message.getValue(),
            message));

        label.visibleProperty().bind(Bindings.createBooleanBinding(
            () -> !isBlank(message.getValue()),
            message));
    }

    private static boolean isBlank(String text)
    {
        return text == null || text.isEmpty();
    }

    private void buildStatusControls()
    {
        ToggleGroup group = new ToggleGroup();

        for(IdeaStatus value : IdeaStatus.values())
        {
            RadioButton button = new RadioButton(value.displayName());
            button.getStyleClass().add("status-option");
            button.setToggleGroup(group);
            button.setUserData(value);
            button.setSelected(value == controller.statusProperty().get());
            statusBox.getChildren().add(button);
        }

        group.selectedToggleProperty().addListener((observable, previous, current) ->
        {
            if(current != null)
            {
                controller.statusProperty().set((IdeaStatus) current.getUserData());
            }
        });
    }

    private void renderTags()
    {
        List<Node> chips = new ArrayList<>(controller.tags().size());

        for(Tag tag : controller.tags())
        {
            chips.add(chipFor(tag));
        }

        tagPane.getChildren().setAll(chips);
    }

    private Node chipFor(Tag tag)
    {
        Label name = new Label(tag.name());

        Button remove = new Button("×");
        remove.getStyleClass().add("tag-remove");
        remove.setOnAction(event -> controller.removeTag(tag));
        remove.setAccessibleText("Remove tag " + tag.name());
        remove.setTooltip(new Tooltip("Remove tag " + tag.name()));

        HBox chip = new HBox(name, remove);
        chip.getStyleClass().add("tag-chip");
        chip.setAlignment(Pos.CENTER_LEFT);

        return chip;
    }

    private void onSave()
    {
        SaveResult result = controller.save();

        switch(result)
        {
            case SAVED -> close();

            case INVALID -> titleField.requestFocus();

            case MISSING -> showMissing();
        }
    }

    private void showMissing()
    {
        Alert alert = new Alert(AlertType.WARNING);
        Theme.applyTo(alert);
        alert.initOwner(owner);
        alert.setTitle("Timeline");
        alert.setHeaderText("This idea no longer exists.");
        alert.setContentText("It was deleted somewhere else, so your changes weren't saved.");
        alert.showAndWait();
        close();
    }
}
