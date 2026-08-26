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

    private Window owner;

    private Scene scene;

    private Runnable closeAction;

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

    private DescriptionArea descriptionArea;

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

    void attach(Window owner, Scene scene, Runnable closeAction)
    {
        this.owner = owner;
        this.scene = Objects.requireNonNull(scene, "scene");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");

        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyFilter);

        Platform.runLater(titleField::requestFocus);
    }

    void detach()
    {
        if(scene != null)
        {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
        }

        if(descriptionArea != null)
        {
            descriptionArea.setOnDescriptionChanged(null);
            descriptionArea.dispose();
        }

        scene = null;
        closeAction = null;
    }

    void requestClose()
    {
        if(confirmDiscard())
        {
            close();
        }
    }

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

    private void buildDescriptionArea()
    {
        descriptionArea = new DescriptionArea();

        VirtualizedScrollPane<DescriptionArea> scroll =
            new VirtualizedScrollPane<>(descriptionArea);
        scroll.getStyleClass().add("description-scroll");

        descriptionStack.getChildren().add(0, scroll);

        loadingDescription = true;
        descriptionArea.load(controller.descriptionProperty().get());
        loadingDescription = false;

        descriptionArea.setOnDescriptionChanged(() ->
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
