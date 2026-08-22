package com.emgi.timeline.view;

import com.emgi.timeline.controller.BlockDraft;
import com.emgi.timeline.controller.BlockKind;
import com.emgi.timeline.controller.IdeaEditorController;
import com.emgi.timeline.controller.IdeaEditorController.SaveResult;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.view.content.BlockEditorFactory;
import com.emgi.timeline.view.content.BlockEditorFactory.BlockRow;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IdeaEditorView
{
    private static final String ERROR_PREFIX = "⚠ ";

    private static final ButtonType DISCARD =
            new ButtonType("Discard", ButtonBar.ButtonData.OK_DONE);

    private static final ButtonType KEEP_EDITING =
            new ButtonType("Keep editing", ButtonBar.ButtonData.CANCEL_CLOSE);

    private final IdeaEditorController controller;

    private final List<BlockRow> rows = new ArrayList<>();

    private Stage stage;

    private BlockEditorFactory blockEditors;

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
    private ScrollPane blockScroll;

    @FXML
    private VBox blockList;

    @FXML
    private Button addTextButton;

    @FXML
    private Button addLinkButton;

    @FXML
    private Button addImageButton;

    @FXML
    private Label descriptionErrorLabel;

    @FXML
    private Button saveButton;

    @FXML
    private Button cancelButton;

    public IdeaEditorView(IdeaEditorController controller)
    {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    void setStage(Stage stage)
    {
        this.stage = Objects.requireNonNull(stage, "stage");

        stage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::onEditorKey);

        stage.setOnCloseRequest(event ->
        {
            if(!confirmDiscard())
            {
                event.consume();
            }
        });

        Platform.runLater(titleField::requestFocus);
    }

    @FXML
    private void initialize()
    {
        if(titleField == null || tagPane == null || statusBox == null || blockScroll == null
            || blockList == null || addTextButton == null || addLinkButton == null
            || addImageButton == null || saveButton == null || cancelButton == null)
        {
            throw new IllegalStateException(
                "FXML injection failed, check fx:id and the fx:controller class name."
            );
        }

        titleField.textProperty().bindBidirectional(controller.titleProperty());

        bindError(titleErrorLabel, controller.titleErrorProperty());
        bindError(tagsErrorLabel, controller.tagsErrorProperty());
        bindError(descriptionErrorLabel, controller.descriptionErrorProperty());

        buildStatusControls();
        buildBlockEditor();

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
        cancelButton.setOnAction(event -> attemptCancel());

        saveButton.setTooltip(new Tooltip("Save  (Ctrl+Enter)"));
        cancelButton.setTooltip(new Tooltip("Cancel  (Esc)"));
    }

    private void attemptCancel()
    {
        if(confirmDiscard())
        {
            stage.close();
        }
    }

    private boolean confirmDiscard()
    {
        if(!controller.isDirty())
        {
            return true;
        }

        Alert confirm = new Alert(AlertType.CONFIRMATION);
        Theme.applyTo(confirm);
        confirm.initOwner(stage);
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
        if(event.isAltDown() && event.getCode() == KeyCode.UP)
        {
            moveFocusedBlock(true);
            event.consume();
            return;
        }

        if(event.isAltDown() && event.getCode() == KeyCode.DOWN)
        {
            moveFocusedBlock(false);
            event.consume();
            return;
        }

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
            case T ->
            {
                addBlock(BlockKind.TEXT);
                event.consume();
            }
            case L ->
            {
                addBlock(BlockKind.LINK);
                event.consume();
            }
            case I ->
            {
                addBlock(BlockKind.IMAGE);
                event.consume();
            }
            default ->
            {
            }
        }
    }

    private void moveFocusedBlock(boolean up)
    {
        BlockDraft draft = focusedDraft();

        if(draft == null)
        {
            return;
        }

        if(up)
        {
            controller.moveBlockUp(draft);
        }
        else
        {
            controller.moveBlockDown(draft);
        }

        focusBlock(draft);
    }

    private BlockDraft focusedDraft()
    {
        Node node = stage.getScene().getFocusOwner();

        while(node != null)
        {
            if(node.getUserData() instanceof BlockDraft draft)
            {
                return draft;
            }

            node = node.getParent();
        }

        return null;
    }

    private void focusBlock(BlockDraft draft)
    {
        for(BlockRow row : rows)
        {
            if(row.draft() == draft)
            {
                row.focusTarget().requestFocus();
                return;
            }
        }
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

    private void buildBlockEditor()
    {
        blockEditors = new BlockEditorFactory(controller);

        rebuildBlocks();
        controller.blocks().addListener((ListChangeListener<BlockDraft>) change -> rebuildBlocks());

        addTextButton.setOnAction(event -> addBlock(BlockKind.TEXT));
        addLinkButton.setOnAction(event -> addBlock(BlockKind.LINK));
        addImageButton.setOnAction(event -> addBlock(BlockKind.IMAGE));

        addTextButton.setTooltip(new Tooltip("Add a text block  (Ctrl+T)"));
        addLinkButton.setTooltip(new Tooltip("Add a link block  (Ctrl+L)"));
        addImageButton.setTooltip(new Tooltip("Add an image block  (Ctrl+I)"));

        blockScroll.setFocusTraversable(false);
    }

    private void rebuildBlocks()
    {
        int count = controller.blocks().size();

        rows.clear();
        List<Node> nodes = new ArrayList<>(count);

        for(int i = 0; i < count; i++)
        {
            BlockRow row = blockEditors.create(controller.blocks().get(i), i == 0, i == count - 1);
            rows.add(row);
            nodes.add(row.node());
        }

        blockList.getChildren().setAll(nodes);
    }

    private void addBlock(BlockKind kind)
    {
        BlockDraft draft = controller.addBlock(kind);
        blockScroll.setVvalue(1.0);
        focusBlock(draft);
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
            case SAVED -> stage.close();

            case INVALID -> titleField.requestFocus();

            case MISSING -> showMissing();
        }
    }

    private void showMissing()
    {
        Alert alert = new Alert(AlertType.WARNING);
        Theme.applyTo(alert);
        alert.initOwner(stage);
        alert.setTitle("Timeline");
        alert.setHeaderText("This idea no longer exists.");
        alert.setContentText("It was deleted somewhere else, so your changes weren't saved.");
        alert.showAndWait();
        stage.close();
    }
}
